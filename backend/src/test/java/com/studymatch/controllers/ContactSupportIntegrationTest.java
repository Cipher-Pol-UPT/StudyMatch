package com.studymatch.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.studymatch.enums.TicketCategory;
import com.studymatch.models.Degree;
import com.studymatch.models.PersonalityProfile;
import com.studymatch.models.Student;
import com.studymatch.models.Ticket;
import com.studymatch.repositories.DegreeRepository;
import com.studymatch.repositories.StudentRepository;
import com.studymatch.repositories.TicketRepository;
import com.studymatch.utils.EmailSender;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ContactSupportIntegrationTest {

  private static final String PASSWORD = "Test1234";
  private static final String EMAIL = "it.support.guest@example.com";
  private static final String IP = "10.20.30.40";

  @Autowired
  private MockMvc mockMvc;
  @Autowired
  private PasswordEncoder passwordEncoder;
  @Autowired
  private DegreeRepository degreeRepository;
  @Autowired
  private StudentRepository studentRepository;
  @Autowired
  private TicketRepository ticketRepository;
  @MockitoBean
  private EmailSender emailSender;

  private ResultActions open(String email, String ip) throws Exception {
    return mockMvc.perform(post("/support/tickets/").with(csrf())
        .with(request -> {
          request.setRemoteAddr(ip);
          return request;
        })
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"email\":\"" + email + "\",\"category\":\"ACCOUNT\",\"subject\":\"locked out\","
            + "\"description\":\"my account says blocked\"}"));
  }

  private ResultActions reply(String token, String text) throws Exception {
    return mockMvc.perform(post("/support/tickets/" + token + "/replies").with(csrf())
        .contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"" + text + "\"}"));
  }

  // the token is only in the email, so it comes from what the email was sent with
  private String openAndGetToken() throws Exception {
    open(EMAIL, IP).andExpect(status().isCreated());
    ArgumentCaptor<String> link = ArgumentCaptor.forClass(String.class);
    verify(emailSender).sendSupportTicketLink(eq(EMAIL), eq("locked out"), link.capture());
    return link.getValue().substring(link.getValue().lastIndexOf('/') + 1);
  }

  @Test
  @DisplayName("POST /support/tickets/ without logging in saves an Open ticket and answers 201 with no body")
  void openWithoutLogin() throws Exception {
    // Act + Assert
    open(EMAIL, IP)
        .andExpect(status().isCreated())
        .andExpect(content().string(""));
    assertEquals(1, ticketRepository.countByGuestEmailIgnoreCaseAndCreatedAtAfter(EMAIL,
        LocalDateTime.now().minusHours(1)));
  }

  @Test
  @DisplayName("The emailed link opens the ticket with its thread, without logging in")
  void viewThroughLink() throws Exception {
    // Arrange
    String token = openAndGetToken();

    // Act + Assert
    mockMvc.perform(get("/support/tickets/" + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.category").value("ACCOUNT"))
        .andExpect(jsonPath("$.status").value("OPEN"))
        .andExpect(jsonPath("$.description").value("my account says blocked"))
        .andExpect(jsonPath("$.replies.length()").value(0));
  }

  @Test
  @DisplayName("Replying through the link adds a reply with no author")
  void replyThroughLink() throws Exception {
    // Arrange
    String token = openAndGetToken();

    // Act + Assert
    reply(token, "any news?")
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.replies[0].text").value("any news?"))
        .andExpect(jsonPath("$.replies[0].authorNumber").doesNotExist());
  }

  @Test
  @DisplayName("A wrong link is NOT_FOUND, to read and to reply")
  void wrongLink() throws Exception {
    // Arrange
    openAndGetToken();

    // Act + Assert
    mockMvc.perform(get("/support/tickets/not-the-token"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    reply("not-the-token", "hi")
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
  }

  @Test
  @DisplayName("The 4th ticket from one email in an hour, whatever its case, is TOO_MANY_TICKETS")
  void tooManyFromEmail() throws Exception {
    // Arrange
    for (int i = 0; i < Ticket.MAX_GUEST_TICKETS_PER_EMAIL; i++) {
      open(EMAIL, "10.0.0." + i).andExpect(status().isCreated());
    }

    // Act + Assert
    open(EMAIL.toUpperCase(), "10.0.0.99")
        .andExpect(status().isTooManyRequests())
        .andExpect(jsonPath("$.code").value("TOO_MANY_TICKETS"))
        .andExpect(jsonPath("$.params.max").value(3));
  }

  @Test
  @DisplayName("The 11th ticket from one ip in an hour is TOO_MANY_TICKETS, even from new emails")
  void tooManyFromIp() throws Exception {
    // Arrange
    for (int i = 0; i < Ticket.MAX_GUEST_TICKETS_PER_IP; i++) {
      open("it.support.guest" + i + "@example.com", IP).andExpect(status().isCreated());
    }

    // Act + Assert
    open("it.support.another@example.com", IP)
        .andExpect(status().isTooManyRequests())
        .andExpect(jsonPath("$.params.max").value(10));
  }

  @Test
  @DisplayName("POST /support/tickets/ with an invalid email is a field error on email")
  void invalidEmail() throws Exception {
    // Act + Assert
    open("not-an-email", IP)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.fieldErrors[0].field").value("email"));
  }

  @Test
  @DisplayName("A logged-in user can't reach a guest's ticket by its id")
  void notReachableById() throws Exception {
    // Arrange
    openAndGetToken();
    Long guestTicketId = ticketRepository.findAll().stream().filter(Ticket::isFromGuest).findFirst().orElseThrow()
        .getId();
    Degree degree = degreeRepository.save(new Degree("IT Support Degree", 3));
    Student student = new Student("it.support.ana@example.com", "Ana Test", passwordEncoder.encode(PASSWORD), degree,
        1);
    student.setMustChangePassword(false);
    student.setPersonalityProfile(new PersonalityProfile(0.5, 0.5, 0.5, 0.5, 0.5));
    student = studentRepository.save(student);
    student.generateInstitutionalEmail("studymatch.com");
    studentRepository.saveAndFlush(student);
    MvcResult login = mockMvc.perform(post("/users/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
        .content("{\"email\":\"" + student.getInstitutionalEmail() + "\",\"password\":\"" + PASSWORD + "\"}"))
        .andReturn();
    MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);

    // Act + Assert
    mockMvc.perform(get("/tickets/" + guestTicketId).session(session)).andExpect(status().isNotFound());
    mockMvc.perform(get("/tickets/mine").session(session)).andExpect(jsonPath("$.length()").value(0));
  }

  @Test
  @DisplayName("The database refuses a ticket with neither an author nor a guest email")
  void authorOrGuest() {
    // Arrange
    Ticket orphan = new Ticket(null, TicketCategory.OTHER, "help", "nobody wrote this");

    // Act + Assert
    assertThrows(DataIntegrityViolationException.class, () -> ticketRepository.saveAndFlush(orphan));
  }

}
