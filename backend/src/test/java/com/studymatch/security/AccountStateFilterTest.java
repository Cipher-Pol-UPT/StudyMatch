package com.studymatch.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.studymatch.enums.AccountStatus;
import com.studymatch.models.Degree;
import com.studymatch.models.Student;
import com.studymatch.models.Teacher;
import com.studymatch.models.User;
import com.studymatch.services.UserService;

import tools.jackson.databind.json.JsonMapper;

class AccountStateFilterTest {

  private static final String EMAIL = "user@test.com";

  private UserService userService;
  private AccountStateFilter filter;
  private MockHttpServletResponse response;
  private MockFilterChain chain;

  @BeforeEach
  void setUp() {
    userService = mock(UserService.class);
    filter = new AccountStateFilter(userService, new SecurityErrorWriter(JsonMapper.builder().build()));
    response = new MockHttpServletResponse();
    chain = new MockFilterChain();
  }

  @AfterEach
  void clearLogin() {
    // the logged-in user is shared static state, don't let it leak into the next test
    SecurityContextHolder.clearContext();
  }

  // pretend this user is logged in
  private void logInAs(User user) {
    SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(EMAIL, null, List.of()));
    when(userService.findByInstitutionalEmail(EMAIL)).thenReturn(Optional.ofNullable(user));
  }

  private Teacher activeTeacher() {
    Teacher teacher = new Teacher("t@example.com", EMAIL, "Test Teacher", "hash");
    teacher.setMustChangePassword(false);
    return teacher;
  }

  private Student studentWithoutTest() {
    Student student = new Student("s@example.com", "Test Student", "hash", new Degree("Test Degree", 3), 1);
    student.setMustChangePassword(false);
    return student;
  }

  private void runFilter(String method, String path) throws Exception {
    filter.doFilter(new MockHttpServletRequest(method, path), response, chain);
  }

  private boolean passedThrough() {
    return chain.getRequest() != null;
  }

  // who gets through

  @Test
  @DisplayName("Requests from people who aren't logged in pass untouched")
  void anonymousRequestPasses() throws Exception {
    // Act - nobody logged in
    runFilter("GET", "/users/me");

    // Assert - and the db isn't even asked
    assertTrue(passedThrough());
    verify(userService, never()).findByInstitutionalEmail(anyString());
  }

  @Test
  @DisplayName("A normal active user passes")
  void activeUserPasses() throws Exception {
    // Arrange
    logInAs(activeTeacher());

    // Act
    runFilter("GET", "/degrees/");

    // Assert
    assertTrue(passedThrough());
  }

  // deleted / blocked

  @Test
  @DisplayName("A user that no longer exists is logged out with 401")
  void deletedUserIsLoggedOut() throws Exception {
    // Arrange
    logInAs(null);
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/degrees/");
    MockHttpSession session = new MockHttpSession();
    request.setSession(session);

    // Act
    filter.doFilter(request, response, chain);

    // Assert
    assertEquals(401, response.getStatus());
    assertTrue(response.getContentAsString().contains("UNAUTHENTICATED"));
    assertTrue(session.isInvalid());
    assertFalse(passedThrough());
  }

  @Test
  @DisplayName("A blocked user gets 403 and loses their session")
  void blockedUserIsKickedOut() throws Exception {
    // Arrange - blocked while logged in
    Teacher blocked = activeTeacher();
    blocked.setAccountStatus(AccountStatus.BLOCKED);
    logInAs(blocked);
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/degrees/");
    MockHttpSession session = new MockHttpSession();
    request.setSession(session);

    // Act
    filter.doFilter(request, response, chain);

    // Assert
    assertEquals(403, response.getStatus());
    assertTrue(response.getContentAsString().contains("ACCOUNT_BLOCKED"));
    assertTrue(session.isInvalid());
    assertNull(SecurityContextHolder.getContext().getAuthentication());
    assertFalse(passedThrough());
  }

  // password change gate

  @Test
  @DisplayName("Must change password: other endpoints are blocked")
  void passwordGateBlocksOtherEndpoints() throws Exception {
    // Arrange - new accounts must change their password
    logInAs(new Teacher("t@example.com", EMAIL, "Test Teacher", "hash"));

    // Act
    runFilter("GET", "/degrees/");

    // Assert
    assertEquals(403, response.getStatus());
    assertTrue(response.getContentAsString().contains("PASSWORD_CHANGE_REQUIRED"));
    assertFalse(passedThrough());
  }

  @ParameterizedTest(name = "{0} {1} is allowed")
  @CsvSource({ "GET, /users/me", "PUT, /users/change-password" })
  @DisplayName("Must change password: only /users/me and the password change are allowed")
  void passwordGateAllowsItsEndpoints(String method, String path) throws Exception {
    // Arrange
    logInAs(new Teacher("t@example.com", EMAIL, "Test Teacher", "hash"));

    // Act
    runFilter(method, path);

    // Assert
    assertTrue(passedThrough());
  }

  @Test
  @DisplayName("Password change comes before the personality test")
  void passwordGateComesFirst() throws Exception {
    // Arrange - a brand new student: must change password AND take the test
    Student student = new Student("s@example.com", "Test Student", "hash", new Degree("Test Degree", 3), 1);
    logInAs(student);

    // Act - trying to take the test before changing the password
    runFilter("POST", "/students/personality-test");

    // Assert
    assertEquals(403, response.getStatus());
    assertTrue(response.getContentAsString().contains("PASSWORD_CHANGE_REQUIRED"));
  }

  @Test
  @DisplayName("The allowed endpoints need the right method too")
  void passwordGateChecksMethod() throws Exception {
    // Arrange
    logInAs(new Teacher("t@example.com", EMAIL, "Test Teacher", "hash"));

    // Act - right path, wrong method
    runFilter("GET", "/users/change-password");

    // Assert
    assertEquals(403, response.getStatus());
    assertFalse(passedThrough());
  }

  // personality test gate (GR1)

  @Test
  @DisplayName("Student without the test: other endpoints are blocked")
  void personalityGateBlocksOtherEndpoints() throws Exception {
    // Arrange
    logInAs(studentWithoutTest());

    // Act
    runFilter("GET", "/degrees/");

    // Assert
    assertEquals(403, response.getStatus());
    assertTrue(response.getContentAsString().contains("PERSONALITY_TEST_REQUIRED"));
    assertFalse(passedThrough());
  }

  @ParameterizedTest(name = "{0} {1} is allowed")
  @CsvSource({ "GET, /users/me", "PUT, /users/change-password", "POST, /students/personality-test" })
  @DisplayName("Student without the test: /users/me, password change and the test are allowed")
  void personalityGateAllowsItsEndpoints(String method, String path) throws Exception {
    // Arrange
    logInAs(studentWithoutTest());

    // Act
    runFilter(method, path);

    // Assert
    assertTrue(passedThrough());
  }

}
