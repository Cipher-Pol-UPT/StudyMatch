package com.studymatch.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import com.studymatch.enums.AccountStatus;
import com.studymatch.models.Degree;
import com.studymatch.models.Student;
import com.studymatch.models.Teacher;
import com.studymatch.repositories.DegreeRepository;
import com.studymatch.repositories.StudentRepository;
import com.studymatch.repositories.TeacherRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SecurityIntegrationTest {

  private static final String PASSWORD = "Test1234";

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private TeacherRepository teacherRepository;

  @Autowired
  private StudentRepository studentRepository;

  @Autowired
  private DegreeRepository degreeRepository;

  @Autowired
  private PasswordEncoder passwordEncoder;

  // test users (everything is rolled back after each test)

  private Teacher saveTeacher(String name, boolean mustChangePassword) {
    Teacher teacher = new Teacher(name + "@example.com", name + "@studymatch.com", "Test Teacher",
        passwordEncoder.encode(PASSWORD));
    teacher.setMustChangePassword(mustChangePassword);
    return teacherRepository.save(teacher);
  }

  private Student saveStudentWithoutTest() {
    Degree degree = degreeRepository.save(new Degree("Integration Test Degree"));
    Student student = new Student("it.student@example.com", "Test Student", passwordEncoder.encode(PASSWORD), degree, 1);
    student.setMustChangePassword(false);
    student = studentRepository.save(student);
    student.generateInstitutionalEmail("studymatch.com");
    return student;
  }

  private MvcResult login(String email, String password) throws Exception {
    return mockMvc.perform(post("/users/login").with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
        .andReturn();
  }

  // logs in and gives back the session, like the browser keeping the cookie
  private MockHttpSession loginAndGetSession(String email) throws Exception {
    MvcResult result = login(email, PASSWORD);
    assertEquals(200, result.getResponse().getStatus());
    return (MockHttpSession) result.getRequest().getSession(false);
  }

  // not logged in

  @Test
  @DisplayName("Without a session, protected endpoints answer 401")
  void noSessionIsUnauthenticated() throws Exception {
    mockMvc.perform(get("/users/me"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
  }

  // login

  @Test
  @DisplayName("A correct login returns the user and the session works afterwards")
  void loginAndUseSession() throws Exception {
    // Arrange
    saveTeacher("it.teacher", false);

    // Act
    MockHttpSession session = loginAndGetSession("it.teacher@studymatch.com");

    // Assert - the session from the login is recognised
    mockMvc.perform(get("/users/me").session(session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.institutionalEmail").value("it.teacher@studymatch.com"))
        .andExpect(jsonPath("$.role").value("TEACHER"));
  }

  @ParameterizedTest(name = "{0} / {1}")
  @CsvSource({
      "it.teacher@studymatch.com, WrongPass1", // right email, wrong password
      "nobody@studymatch.com, Test1234"        // unknown email
  })
  @DisplayName("Wrong password and unknown email get the exact same answer")
  void badCredentials(String email, String password) throws Exception {
    // Arrange
    saveTeacher("it.teacher", false);

    // Act + Assert
    mockMvc.perform(post("/users/login").with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
  }

  @Test
  @DisplayName("Login without a CSRF token is rejected")
  void loginWithoutCsrfIsRejected() throws Exception {
    // Arrange
    saveTeacher("it.teacher", false);

    // Act + Assert - no .with(csrf()) here
    mockMvc.perform(post("/users/login")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"email\":\"it.teacher@studymatch.com\",\"password\":\"" + PASSWORD + "\"}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("CSRF_TOKEN_INVALID"));
  }

  @Test
  @DisplayName("An invalid login body gets field error codes")
  void invalidLoginBody() throws Exception {
    mockMvc.perform(post("/users/login").with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"email\":\"not-an-email\",\"password\":\"\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.fieldErrors[?(@.field == 'email')].code").value("EMAIL"))
        .andExpect(jsonPath("$.fieldErrors[?(@.field == 'password')].code").value("NOT_BLANK"));
  }

  // blocked accounts

  @Test
  @DisplayName("A blocked user can't log in and gets no session")
  void blockedUserCannotLogIn() throws Exception {
    // Arrange
    Teacher teacher = saveTeacher("it.teacher", false);
    teacher.setAccountStatus(AccountStatus.BLOCKED);

    // Act
    MvcResult result = login("it.teacher@studymatch.com", PASSWORD);

    // Assert
    assertEquals(403, result.getResponse().getStatus());
    assertTrue(result.getResponse().getContentAsString().contains("ACCOUNT_BLOCKED"));
    MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
    assertTrue(session == null
        || session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY) == null);
  }

  @Test
  @DisplayName("A user blocked while logged in loses access on the next request")
  void blockedWhileLoggedIn() throws Exception {
    // Arrange
    Teacher teacher = saveTeacher("it.teacher", false);
    MockHttpSession session = loginAndGetSession("it.teacher@studymatch.com");

    // Act - an admin blocks them
    teacher.setAccountStatus(AccountStatus.BLOCKED);

    // Assert
    mockMvc.perform(get("/users/me").session(session))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("ACCOUNT_BLOCKED"));
    assertTrue(session.isInvalid());
  }

  // onboarding gates

  @Test
  @DisplayName("With a one-time password only /users/me (and the password change) work")
  void passwordGate() throws Exception {
    // Arrange
    saveTeacher("it.newteacher", true);
    MockHttpSession session = loginAndGetSession("it.newteacher@studymatch.com");

    // Act + Assert
    mockMvc.perform(get("/users/me").session(session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.mustChangePassword").value(true));
    mockMvc.perform(get("/degrees/").session(session))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"));
  }

  @Test
  @DisplayName("A student without the personality test can only reach the allowed endpoints")
  void personalityGate() throws Exception {
    // Arrange
    Student student = saveStudentWithoutTest();
    MockHttpSession session = loginAndGetSession(student.getInstitutionalEmail());

    // Act + Assert
    mockMvc.perform(get("/degrees/").session(session))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("PERSONALITY_TEST_REQUIRED"));

    // gets past the gate; 404 only because the endpoint isn't built yet (#6), update this when it is
    mockMvc.perform(post("/students/personality-test").with(csrf()).session(session))
        .andExpect(status().isNotFound());
  }

  // logout + misc

  @Test
  @DisplayName("Logout ends the session")
  void logoutEndsSession() throws Exception {
    // Arrange
    saveTeacher("it.teacher", false);
    MockHttpSession session = loginAndGetSession("it.teacher@studymatch.com");

    // Act
    mockMvc.perform(post("/users/logout").with(csrf()).session(session))
        .andExpect(status().isNoContent());

    // Assert - session gone, and without one we're back to 401
    assertTrue(session.isInvalid());
    mockMvc.perform(get("/users/me"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("Unknown URLs are 404 NOT_FOUND when logged in")
  void unknownUrlWhenLoggedIn() throws Exception {
    // Arrange
    saveTeacher("it.teacher", false);
    MockHttpSession session = loginAndGetSession("it.teacher@studymatch.com");

    // Act + Assert
    mockMvc.perform(get("/this-does-not-exist").session(session))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
  }

  @Test
  @DisplayName("Passwords are hashed with BCrypt")
  void passwordsUseBcrypt() {
    assertInstanceOf(BCryptPasswordEncoder.class, passwordEncoder);
  }

  @Test
  @DisplayName("A failed login doesn't leave anyone logged in")
  void failedLoginLeavesNoLogin() throws Exception {
    // Arrange
    saveTeacher("it.teacher", false);

    // Act
    MvcResult result = login("it.teacher@studymatch.com", "WrongPass1");

    // Assert
    MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
    assertNull(session == null ? null
        : session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY));
  }

}
