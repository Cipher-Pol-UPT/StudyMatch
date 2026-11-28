package com.studymatch.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.studymatch.enums.TicketCategory;
import com.studymatch.enums.TicketStatus;
import com.studymatch.models.Admin;
import com.studymatch.models.Degree;
import com.studymatch.models.PersonalityProfile;
import com.studymatch.models.Staff;
import com.studymatch.models.Student;
import com.studymatch.models.Ticket;
import com.studymatch.repositories.AdminRepository;
import com.studymatch.repositories.DegreeRepository;
import com.studymatch.repositories.StaffRepository;
import com.studymatch.repositories.StudentRepository;
import com.studymatch.repositories.TicketRepository;
import com.studymatch.utils.EmailSender;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AnswerTicketIntegrationTest {

  private static final String PASSWORD = "Test1234";
  private static final String GUEST = "it.helpdesk.guest@example.com";

  @Autowired
  private MockMvc mockMvc;
  @Autowired
  private PasswordEncoder passwordEncoder;
  @Autowired
  private DegreeRepository degreeRepository;
  @Autowired
  private StudentRepository studentRepository;
  @Autowired
  private StaffRepository staffRepository;
  @Autowired
  private AdminRepository adminRepository;
  @Autowired
  private TicketRepository ticketRepository;
  @MockitoBean
  private EmailSender emailSender;

  private Student ana;
  private MockHttpSession anaSession;
  private MockHttpSession staffSession;
  private MockHttpSession adminSession;

  @BeforeEach
  void setUp() throws Exception {
    Degree degree = degreeRepository.save(new Degree("IT Helpdesk Degree", 3));
    ana = new Student("it.helpdesk.ana@example.com", "Ana Test", passwordEncoder.encode(PASSWORD), degree, 1);
    ana.setMustChangePassword(false);
    ana.setPersonalityProfile(new PersonalityProfile(0.5, 0.5, 0.5, 0.5, 0.5));
    ana = studentRepository.save(ana);
    ana.generateInstitutionalEmail("studymatch.com");
    ana = studentRepository.saveAndFlush(ana);

    Staff staff = new Staff("it.helpdesk.staff@example.com", "it.helpdesk.staff@studymatch.com", "Staff Test",
        passwordEncoder.encode(PASSWORD));
    staff.setMustChangePassword(false);
    staffRepository.saveAndFlush(staff);
    Admin admin = new Admin("it.helpdesk.admin@example.com", "it.helpdesk.admin@studymatch.com", "Admin Test",
        passwordEncoder.encode(PASSWORD));
    admin.setMustChangePassword(false);
    adminRepository.saveAndFlush(admin);

    anaSession = login(ana.getInstitutionalEmail());
    staffSession = login(staff.getInstitutionalEmail());
    adminSession = login(admin.getInstitutionalEmail());
  }

  private MockHttpSession login(String email) throws Exception {
    MvcResult result = mockMvc.perform(post("/users/login").with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
        .andReturn();
    assertEquals(200, result.getResponse().getStatus());
    return (MockHttpSession) result.getRequest().getSession(false);
  }

  // the last activity is set by hand, so the order inside a status is known
  private Ticket saveTicket(TicketCategory category, TicketStatus status, String subject, int minutesAgo) {
    Ticket ticket = new Ticket(ana, category, subject, "details");
    ReflectionTestUtils.setField(ticket, "status", status);
    ReflectionTestUtils.setField(ticket, "updatedAt", LocalDateTime.now().minusMinutes(minutesAgo));
    return ticketRepository.saveAndFlush(ticket);
  }

  private ResultActions answer(MockHttpSession as, Long ticketId, String text) throws Exception {
    return mockMvc.perform(post("/helpdesk/tickets/" + ticketId + "/replies").with(csrf()).session(as)
        .contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"" + text + "\"}"));
  }

  private ResultActions resolve(MockHttpSession as, Long ticketId) throws Exception {
    return mockMvc.perform(put("/helpdesk/tickets/" + ticketId + "/resolve").with(csrf()).session(as));
  }

  private static String sha256(String text) throws Exception {
    return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
  }

  @Test
  @DisplayName("GET /helpdesk/tickets/ as Staff is their queue only: Open first, newest first, with the Open count")
  void staffQueue() throws Exception {
    // Arrange
    saveTicket(TicketCategory.OTHER, TicketStatus.RESOLVED, "resolved", 1);
    saveTicket(TicketCategory.TECHNICAL, TicketStatus.IN_PROGRESS, "in progress", 2);
    saveTicket(TicketCategory.TECHNICAL, TicketStatus.OPEN, "older open", 30);
    saveTicket(TicketCategory.GRADES_AND_ENROLMENT, TicketStatus.OPEN, "newer open", 5);
    saveTicket(TicketCategory.ACCOUNT, TicketStatus.OPEN, "account", 0);

    // Act + Assert
    mockMvc.perform(get("/helpdesk/tickets/").session(staffSession))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.openCount").value(2))
        .andExpect(jsonPath("$.tickets.totalElements").value(4))
        .andExpect(jsonPath("$.tickets.content[0].subject").value("newer open"))
        .andExpect(jsonPath("$.tickets.content[1].subject").value("older open"))
        .andExpect(jsonPath("$.tickets.content[2].subject").value("in progress"))
        .andExpect(jsonPath("$.tickets.content[3].subject").value("resolved"))
        .andExpect(jsonPath("$.tickets.content[0].authorName").value("Ana Test"));
  }

  @Test
  @DisplayName("GET /helpdesk/tickets/ as Admin is only the Account queue")
  void adminQueue() throws Exception {
    // Arrange
    saveTicket(TicketCategory.TECHNICAL, TicketStatus.OPEN, "wifi", 1);
    saveTicket(TicketCategory.ACCOUNT, TicketStatus.OPEN, "locked out", 1);

    // Act + Assert
    mockMvc.perform(get("/helpdesk/tickets/").session(adminSession))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.openCount").value(1))
        .andExpect(jsonPath("$.tickets.content.length()").value(1))
        .andExpect(jsonPath("$.tickets.content[0].subject").value("locked out"));
  }

  @Test
  @DisplayName("A ticket from the other queue is NOT_FOUND to view, answer and resolve, and stays Open")
  void otherQueue() throws Exception {
    // Arrange
    Ticket account = saveTicket(TicketCategory.ACCOUNT, TicketStatus.OPEN, "locked out", 1);

    // Act + Assert
    mockMvc.perform(get("/helpdesk/tickets/" + account.getId()).session(staffSession))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    answer(staffSession, account.getId(), "hi").andExpect(status().isNotFound());
    resolve(staffSession, account.getId()).andExpect(status().isNotFound());
    assertEquals(TicketStatus.OPEN, ticketRepository.findById(account.getId()).orElseThrow().getStatus());
  }

  @Test
  @DisplayName("Staff answer, Ana is notified, Staff resolve, Ana is notified, and Ana writing back opens it again")
  void answerResolveReopen() throws Exception {
    // Arrange
    Ticket ticket = saveTicket(TicketCategory.TECHNICAL, TicketStatus.OPEN, "wifi", 1);

    // Act + Assert
    answer(staffSession, ticket.getId(), "try eduroam")
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
        .andExpect(jsonPath("$.authorName").value("Ana Test"))
        .andExpect(jsonPath("$.replies[0].authorRole").value("STAFF"))
        .andExpect(jsonPath("$.replies[0].text").value("try eduroam"));
    resolve(staffSession, ticket.getId())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("RESOLVED"));
    mockMvc.perform(get("/notifications/").session(anaSession))
        .andExpect(jsonPath("$.content.length()").value(2))
        .andExpect(jsonPath("$.content[0].type").value("TICKET_RESOLVED"))
        .andExpect(jsonPath("$.content[1].type").value("TICKET_ANSWERED"))
        .andExpect(jsonPath("$.content[1].target").value("TICKET"))
        .andExpect(jsonPath("$.content[1].targetId").value(ticket.getId()))
        .andExpect(jsonPath("$.content[1].params.subject").value("wifi"));
    mockMvc.perform(post("/tickets/" + ticket.getId() + "/replies").with(csrf()).session(anaSession)
        .contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"still broken\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("OPEN"));
  }

  @Test
  @DisplayName("Resolving a Resolved ticket is TICKET_ALREADY_RESOLVED and doesn't notify Ana again")
  void resolveTwice() throws Exception {
    // Arrange
    Ticket ticket = saveTicket(TicketCategory.OTHER, TicketStatus.OPEN, "help", 1);
    resolve(staffSession, ticket.getId()).andExpect(status().isOk());

    // Act + Assert
    resolve(staffSession, ticket.getId())
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("TICKET_ALREADY_RESOLVED"));
    mockMvc.perform(get("/notifications/").session(anaSession))
        .andExpect(jsonPath("$.content.length()").value(1));
  }

  @Test
  @DisplayName("Answering a guest's ticket emails a new link, the old link is NOT_FOUND and the new one shows it")
  void guestNewLink() throws Exception {
    // Arrange
    Ticket guest = ticketRepository.saveAndFlush(Ticket.forGuest(GUEST, "10.0.0.7", sha256("old-token"),
        TicketCategory.ACCOUNT, "locked out", "my account says blocked"));
    ArgumentCaptor<String> link = ArgumentCaptor.forClass(String.class);

    // Act
    answer(adminSession, guest.getId(), "unblocked it")
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.guestEmail").value(GUEST));

    // Assert
    verify(emailSender).sendSupportTicketUpdate(eq(GUEST), eq("locked out"), link.capture(), eq(false));
    String token = link.getValue().substring(link.getValue().lastIndexOf('/') + 1);
    mockMvc.perform(get("/support/tickets/old-token")).andExpect(status().isNotFound());
    mockMvc.perform(get("/support/tickets/" + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
        .andExpect(jsonPath("$.replies[0].text").value("unblocked it"));
  }

  @Test
  @DisplayName("A student can't see a queue, ACCESS_DENIED")
  void studentDenied() throws Exception {
    // Act + Assert
    mockMvc.perform(get("/helpdesk/tickets/").session(anaSession))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
  }

}
