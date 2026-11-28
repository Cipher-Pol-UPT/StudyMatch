package com.studymatch.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
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
import com.studymatch.models.Student;
import com.studymatch.models.Ticket;
import com.studymatch.repositories.AdminRepository;
import com.studymatch.repositories.DegreeRepository;
import com.studymatch.repositories.StudentRepository;
import com.studymatch.repositories.TicketRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class OpenTicketIntegrationTest {

  private static final String PASSWORD = "Test1234";

  @Autowired
  private MockMvc mockMvc;
  @Autowired
  private PasswordEncoder passwordEncoder;
  @Autowired
  private DegreeRepository degreeRepository;
  @Autowired
  private StudentRepository studentRepository;
  @Autowired
  private AdminRepository adminRepository;
  @Autowired
  private TicketRepository ticketRepository;

  private Student ana;
  private Student rui;
  private MockHttpSession session;

  @BeforeEach
  void setUp() throws Exception {
    ana = saveStudent("it.tickets.ana@example.com", "Ana Test", true);
    rui = saveStudent("it.tickets.rui@example.com", "Rui Test", true);
    session = login(ana.getInstitutionalEmail());
  }

  private Student saveStudent(String personalEmail, String name, boolean tookTest) {
    Degree degree = degreeRepository.save(new Degree("IT Tickets Degree " + name, 3));
    Student student = new Student(personalEmail, name, passwordEncoder.encode(PASSWORD), degree, 1);
    student.setMustChangePassword(false);
    if (tookTest) {
      student.setPersonalityProfile(new PersonalityProfile(0.5, 0.5, 0.5, 0.5, 0.5));
    }
    student = studentRepository.save(student);
    student.generateInstitutionalEmail("studymatch.com");
    return studentRepository.saveAndFlush(student);
  }

  private MockHttpSession login(String email) throws Exception {
    MvcResult result = mockMvc.perform(post("/users/login").with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
        .andReturn();
    assertEquals(200, result.getResponse().getStatus());
    return (MockHttpSession) result.getRequest().getSession(false);
  }

  private ResultActions open(MockHttpSession as, String category, String subject, String description)
      throws Exception {
    return mockMvc.perform(post("/tickets/").with(csrf()).session(as)
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"category\":\"" + category + "\",\"subject\":\"" + subject + "\",\"description\":\""
            + description + "\"}"));
  }

  private ResultActions reply(MockHttpSession as, Long ticketId, String text) throws Exception {
    return mockMvc.perform(post("/tickets/" + ticketId + "/replies").with(csrf()).session(as)
        .contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"" + text + "\"}"));
  }

  private Ticket saveTicket(Student author, TicketStatus status) {
    Ticket ticket = new Ticket(author, TicketCategory.TECHNICAL, "wifi", "no wifi in B204");
    ReflectionTestUtils.setField(ticket, "status", status);
    return ticketRepository.saveAndFlush(ticket);
  }

  @Test
  @DisplayName("POST /tickets/ saves the ticket as Open and answers 201 with it")
  void openTicket() throws Exception {
    // Act + Assert
    open(session, "GRADES_AND_ENROLMENT", "  missing grade  ", "my PL grade is not there")
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.category").value("GRADES_AND_ENROLMENT"))
        .andExpect(jsonPath("$.subject").value("missing grade"))
        .andExpect(jsonPath("$.status").value("OPEN"));
    assertEquals(1, ticketRepository.findByAuthor_NumberOrderByUpdatedAtDescIdDesc(ana.getNumber()).size());
  }

  @Test
  @DisplayName("POST /tickets/ takes a 100 character subject and a 2000 character description")
  void openAtTheLimits() throws Exception {
    // Act + Assert
    open(session, "OTHER", "s".repeat(100), "d".repeat(2000)).andExpect(status().isCreated());
  }

  @Test
  @DisplayName("POST /tickets/ with a 101 character subject is a field error on subject")
  void subjectTooLong() throws Exception {
    // Act + Assert
    open(session, "OTHER", "s".repeat(101), "help")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.fieldErrors[0].field").value("subject"));
  }

  @Test
  @DisplayName("POST /tickets/ with a 2001 character description is a field error on description")
  void descriptionTooLong() throws Exception {
    // Act + Assert
    open(session, "OTHER", "help", "d".repeat(2001))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors[0].field").value("description"));
  }

  @Test
  @DisplayName("POST /tickets/ with a blank description is a field error and saves nothing")
  void blankDescription() throws Exception {
    // Act + Assert
    open(session, "OTHER", "help", "   ")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors[0].field").value("description"));
    assertEquals(0, ticketRepository.findByAuthor_NumberOrderByUpdatedAtDescIdDesc(ana.getNumber()).size());
  }

  @Test
  @DisplayName("POST /tickets/ as Admin is ACCESS_DENIED, Admin answers tickets instead")
  void adminCannotOpen() throws Exception {
    // Arrange
    Admin admin = new Admin("it.tickets.admin@example.com", "it.tickets.admin@studymatch.com", "Test Admin",
        passwordEncoder.encode(PASSWORD));
    admin.setMustChangePassword(false);
    adminRepository.saveAndFlush(admin);
    MockHttpSession adminSession = login(admin.getInstitutionalEmail());

    // Act + Assert
    open(adminSession, "OTHER", "help", "help")
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
  }

  @Test
  @DisplayName("A student who hasn't taken the personality test can still open, list and reply to tickets")
  void beforePersonalityTest() throws Exception {
    // Arrange
    Student newStudent = saveStudent("it.tickets.new@example.com", "New Test", false);
    MockHttpSession newSession = login(newStudent.getInstitutionalEmail());
    Ticket ticket = saveTicket(newStudent, TicketStatus.OPEN);

    // Act + Assert
    open(newSession, "TECHNICAL", "test won't load", "it spins forever").andExpect(status().isCreated());
    mockMvc.perform(get("/tickets/mine").session(newSession)).andExpect(status().isOk());
    reply(newSession, ticket.getId(), "any news?").andExpect(status().isCreated());
    mockMvc.perform(get("/notes/").session(newSession))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("PERSONALITY_TEST_REQUIRED"));
  }

  @Test
  @DisplayName("GET /tickets/mine lists only my tickets, the last one replied to first")
  void listMine() throws Exception {
    // Arrange
    Ticket older = saveTicket(ana, TicketStatus.OPEN);
    Ticket newer = saveTicket(ana, TicketStatus.OPEN);
    saveTicket(rui, TicketStatus.OPEN);
    reply(session, older.getId(), "bump").andExpect(status().isCreated());

    // Act + Assert
    mockMvc.perform(get("/tickets/mine").session(session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[0].id").value(older.getId()))
        .andExpect(jsonPath("$[1].id").value(newer.getId()));
  }

  @Test
  @DisplayName("GET /tickets/{id} gives my ticket with its thread, oldest reply first")
  void viewMine() throws Exception {
    // Arrange
    Ticket ticket = saveTicket(ana, TicketStatus.OPEN);
    reply(session, ticket.getId(), "first").andExpect(status().isCreated());
    reply(session, ticket.getId(), "second").andExpect(status().isCreated());

    // Act + Assert
    mockMvc.perform(get("/tickets/" + ticket.getId()).session(session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.description").value("no wifi in B204"))
        .andExpect(jsonPath("$.replies[0].text").value("first"))
        .andExpect(jsonPath("$.replies[0].authorNumber").value(ana.getNumber()))
        .andExpect(jsonPath("$.replies[1].text").value("second"));
  }

  @Test
  @DisplayName("Someone else's ticket is NOT_FOUND, to read and to reply")
  void notMine() throws Exception {
    // Arrange
    Ticket ruis = saveTicket(rui, TicketStatus.OPEN);

    // Act + Assert
    mockMvc.perform(get("/tickets/" + ruis.getId()).session(session))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    reply(session, ruis.getId(), "hi")
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
  }

  @Test
  @DisplayName("Replying to my Resolved ticket opens it again")
  void replyReopens() throws Exception {
    // Arrange
    Ticket ticket = saveTicket(ana, TicketStatus.RESOLVED);

    // Act + Assert
    reply(session, ticket.getId(), "still broken")
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("OPEN"))
        .andExpect(jsonPath("$.replies[0].text").value("still broken"));
  }

  @Test
  @DisplayName("A reply of 2001 characters is a field error on text")
  void replyTooLong() throws Exception {
    // Arrange
    Ticket ticket = saveTicket(ana, TicketStatus.OPEN);

    // Act + Assert
    reply(session, ticket.getId(), "r".repeat(2001))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors[0].field").value("text"));
  }

}
