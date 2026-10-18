package com.studymatch.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mail.MailSendException;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.studymatch.utils.EmailSender;
import com.studymatch.models.Admin;
import com.studymatch.models.Staff;
import com.studymatch.models.Teacher;
import com.studymatch.repositories.AdminRepository;
import com.studymatch.repositories.StaffRepository;
import com.studymatch.repositories.TeacherRepository;
import com.studymatch.repositories.UserRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CreateAccountIntegrationTest {

  private static final String PASSWORD = "Test1234";
  private static final String NEW_TEACHER = """
      {"type":"TEACHER","name":"New Teacher","personalEmail":"new.teacher@example.com","username":"it.new.teacher"}""";

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private AdminRepository adminRepository;

  @Autowired
  private TeacherRepository teacherRepository;

  @Autowired
  private StaffRepository staffRepository;

  @Autowired
  private UserRepository userRepository;

  @Autowired
  private PasswordEncoder passwordEncoder;

  // the real app, but emails go to this fake instead of a mail server
  @MockitoBean
  private EmailSender emailSender;

  private Admin saveAdmin() {
    Admin admin = new Admin("it.admin@example.com", "it.admin@studymatch.com", "Test Admin", passwordEncoder.encode(PASSWORD));
    admin.setMustChangePassword(false);
    return adminRepository.save(admin);
  }

  private MockHttpSession loginAs(String email, String password) throws Exception {
    MvcResult result = mockMvc.perform(post("/users/login").with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
        .andReturn();
    assertEquals(200, result.getResponse().getStatus());
    return (MockHttpSession) result.getRequest().getSession(false);
  }

  private ResultActions createAccount(MockHttpSession session, String body) throws Exception {
    return mockMvc.perform(post("/users/").with(csrf()).session(session)
        .contentType(MediaType.APPLICATION_JSON)
        .content(body));
  }

  // the admin creates accounts

  @Test
  @DisplayName("Full flow: admin creates a teacher, the teacher logs in with the emailed password and must change it")
  void adminCreatesTeacher() throws Exception {
    // Arrange
    saveAdmin();
    MockHttpSession admin = loginAs("it.admin@studymatch.com", PASSWORD);

    // Act
    createAccount(admin, NEW_TEACHER)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.institutionalEmail").value("it.new.teacher@studymatch.com"))
        .andExpect(jsonPath("$.type").value("TEACHER"))
        .andExpect(jsonPath("$.number").isNumber())
        .andExpect(jsonPath("$.password").doesNotExist());

    // Assert - the password from the "email" works and lands on the change-password gate
    ArgumentCaptor<String> oneTimePassword = ArgumentCaptor.forClass(String.class);
    verify(emailSender).sendAccountCredentials(eq("new.teacher@example.com"), eq("New Teacher"),
        eq("it.new.teacher@studymatch.com"), oneTimePassword.capture());
    MockHttpSession teacher = loginAs("it.new.teacher@studymatch.com", oneTimePassword.getValue());
    mockMvc.perform(get("/users/me").session(teacher))
        .andExpect(jsonPath("$.role").value("TEACHER"))
        .andExpect(jsonPath("$.mustChangePassword").value(true));
  }

  @Test
  @DisplayName("A taken institutional email is 409")
  void institutionalEmailTaken() throws Exception {
    // Arrange
    saveAdmin();
    teacherRepository.save(new Teacher("someone@example.com", "it.new.teacher@studymatch.com", "Old Teacher", "hash"));
    MockHttpSession admin = loginAs("it.admin@studymatch.com", PASSWORD);

    // Act + Assert
    createAccount(admin, NEW_TEACHER)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("INSTITUTIONAL_EMAIL_ALREADY_EXISTS"));
  }

  @Test
  @DisplayName("A taken personal email is 409")
  void personalEmailTaken() throws Exception {
    // Arrange
    saveAdmin();
    teacherRepository.save(new Teacher("new.teacher@example.com", "someone@studymatch.com", "Old Teacher", "hash"));
    MockHttpSession admin = loginAs("it.admin@studymatch.com", PASSWORD);

    // Act + Assert
    createAccount(admin, NEW_TEACHER)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("PERSONAL_EMAIL_ALREADY_EXISTS"));
  }

  @Test
  @DisplayName("An unknown account type is a field error, not a crash")
  void unknownType() throws Exception {
    // Arrange
    saveAdmin();
    MockHttpSession admin = loginAs("it.admin@studymatch.com", PASSWORD);

    // Act + Assert
    createAccount(admin, NEW_TEACHER.replace("TEACHER", "PRINCIPAL"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.fieldErrors[0].field").value("type"))
        .andExpect(jsonPath("$.fieldErrors[0].code").value("INVALID"));
  }

  @Test
  @DisplayName("Creating another admin is ACCOUNT_TYPE_NOT_ALLOWED")
  void adminTypeNotAllowed() throws Exception {
    // Arrange
    saveAdmin();
    MockHttpSession admin = loginAs("it.admin@studymatch.com", PASSWORD);

    // Act + Assert
    createAccount(admin, NEW_TEACHER.replace("TEACHER", "ADMIN"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("ACCOUNT_TYPE_NOT_ALLOWED"));
  }

  // only the admin

  @Test
  @DisplayName("A teacher can't create accounts (403 ACCESS_DENIED)")
  void teacherIsDenied() throws Exception {
    // Arrange
    Teacher teacher = new Teacher("it.teacher@example.com", "it.teacher@studymatch.com", "Test Teacher",
        passwordEncoder.encode(PASSWORD));
    teacher.setMustChangePassword(false);
    teacherRepository.save(teacher);
    MockHttpSession session = loginAs("it.teacher@studymatch.com", PASSWORD);

    // Act + Assert
    createAccount(session, NEW_TEACHER)
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
  }

  @Test
  @DisplayName("Staff can't create accounts either (403 ACCESS_DENIED)")
  void staffIsDenied() throws Exception {
    // Arrange
    Staff staff = new Staff("it.staff@example.com", "it.staff@studymatch.com", "Test Staff", passwordEncoder.encode(PASSWORD));
    staff.setMustChangePassword(false);
    staffRepository.save(staff);
    MockHttpSession session = loginAs("it.staff@studymatch.com", PASSWORD);

    // Act + Assert
    createAccount(session, NEW_TEACHER)
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
  }

  @Test
  @DisplayName("Without logging in it's 401")
  void notLoggedIn() throws Exception {
    mockMvc.perform(post("/users/").with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content(NEW_TEACHER))
        .andExpect(status().isUnauthorized());
  }

  // the email fails

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  @DisplayName("If the email can't be sent the account isn't created at all (502 EMAIL_NOT_SENT)")
  void failedEmailRollsBack() throws Exception {
    // Arrange - no test transaction here, so the service's own rollback really happens (cleaned up at the end)
    Admin savedAdmin = saveAdmin();
    try {
      MockHttpSession admin = loginAs("it.admin@studymatch.com", PASSWORD);
      doThrow(new MailSendException("mail server down"))
          .when(emailSender).sendAccountCredentials(any(), any(), any(), any());

      // Act
      createAccount(admin, NEW_TEACHER)
          .andExpect(status().isBadGateway())
          .andExpect(jsonPath("$.code").value("EMAIL_NOT_SENT"));

      // Assert - nothing left behind, so the admin can fix the email and try again
      assertFalse(userRepository.existsByInstitutionalEmail("it.new.teacher@studymatch.com"));
    } finally {
      adminRepository.delete(savedAdmin);
    }
  }

}
