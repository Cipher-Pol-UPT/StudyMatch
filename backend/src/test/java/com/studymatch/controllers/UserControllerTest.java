package com.studymatch.controllers;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;

import com.studymatch.dtos.ChangePasswordRequestDTO;
import com.studymatch.dtos.CurrentUserDTO;
import com.studymatch.dtos.LoginRequestDTO;
import com.studymatch.enums.AccountStatus;
import com.studymatch.enums.Role;
import com.studymatch.exceptions.ErrorCode;
import com.studymatch.exceptions.StudyMatchException;
import com.studymatch.models.Teacher;
import com.studymatch.services.UserService;

class UserControllerTest {

  private static final String EMAIL = "teacher@test.com";
  private static final LoginRequestDTO LOGIN = new LoginRequestDTO(EMAIL, "Demo1234");

  private UserService userService;
  private AuthenticationManager authenticationManager;
  private SecurityContextRepository securityContextRepository;
  private UserController controller;

  private Teacher teacher;
  private Authentication authenticated;
  private MockHttpServletRequest request;
  private MockHttpServletResponse response;

  @BeforeEach
  void setUp() {
    userService = mock(UserService.class);
    authenticationManager = mock(AuthenticationManager.class);
    securityContextRepository = mock(SecurityContextRepository.class);
    controller = new UserController(userService, authenticationManager, securityContextRepository);

    teacher = new Teacher("t@example.com", EMAIL, "Test Teacher", "hash");
    teacher.setMustChangePassword(false);
    authenticated = UsernamePasswordAuthenticationToken.authenticated(EMAIL, null, List.of());
    request = new MockHttpServletRequest("POST", "/users/login");
    response = new MockHttpServletResponse();
  }

  @AfterEach
  void clearLogin() {
    SecurityContextHolder.clearContext();
  }

  // "the password was right"
  private void passwordIsCorrect() {
    when(authenticationManager.authenticate(any())).thenReturn(authenticated);
    when(userService.getByInstitutionalEmail(EMAIL)).thenReturn(teacher);
  }

  // login

  @Test
  @DisplayName("A correct login returns the user's data")
  void loginReturnsUser() {
    // Arrange
    passwordIsCorrect();

    // Act
    ResponseEntity<CurrentUserDTO> result = controller.login(LOGIN, request, response);

    // Assert
    assertEquals(200, result.getStatusCode().value());
    assertEquals(EMAIL, result.getBody().institutionalEmail());
    assertEquals(Role.TEACHER, result.getBody().role());
    assertFalse(result.getBody().mustChangePassword());
  }

  @Test
  @DisplayName("A correct login is saved in the session")
  void loginIsSaved() {
    // Arrange
    passwordIsCorrect();

    // Act
    controller.login(LOGIN, request, response);

    // Assert
    verify(securityContextRepository).saveContext(any(), eq(request), eq(response));
    assertSame(authenticated, SecurityContextHolder.getContext().getAuthentication());
  }

  @Test
  @DisplayName("Logging in gives the session a new id (session fixation)")
  void loginChangesSessionId() {
    // Arrange - someone already had a session before logging in
    passwordIsCorrect();
    MockHttpSession session = new MockHttpSession();
    request.setSession(session);
    String idBefore = session.getId();

    // Act
    controller.login(LOGIN, request, response);

    // Assert
    assertNotEquals(idBefore, request.getSession(false).getId());
  }

  @Test
  @DisplayName("Logging in without an existing session works")
  void loginWithoutSession() {
    // Arrange
    passwordIsCorrect();

    // Act + Assert - no session to change the id of
    assertDoesNotThrow(() -> controller.login(LOGIN, request, response));
  }

  @Test
  @DisplayName("A wrong password saves nothing and doesn't even look the user up")
  void wrongPasswordSavesNothing() {
    // Arrange
    when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("bad credentials"));

    // Act - the handler turns this into INVALID_CREDENTIALS
    assertThrows(BadCredentialsException.class, () -> controller.login(LOGIN, request, response));

    // Assert
    verify(userService, never()).getByInstitutionalEmail(anyString());
    verify(securityContextRepository, never()).saveContext(any(), any(), any());
  }

  @Test
  @DisplayName("A blocked user with the right password gets ACCOUNT_BLOCKED and no session")
  void blockedUserGetsNoSession() {
    // Arrange
    passwordIsCorrect();
    teacher.setAccountStatus(AccountStatus.BLOCKED);

    // Act
    StudyMatchException e = assertThrows(StudyMatchException.class, () -> controller.login(LOGIN, request, response));

    // Assert
    assertEquals(ErrorCode.ACCOUNT_BLOCKED, e.getCode());
    verify(securityContextRepository, never()).saveContext(any(), any(), any());
    assertNull(SecurityContextHolder.getContext().getAuthentication());
  }

  // current user

  @Test
  @DisplayName("/users/me returns the logged-in user")
  void meReturnsLoggedInUser() {
    // Arrange
    when(userService.getByInstitutionalEmail(EMAIL)).thenReturn(teacher);

    // Act
    ResponseEntity<CurrentUserDTO> result = controller.me(authenticated);

    // Assert
    assertEquals(200, result.getStatusCode().value());
    assertEquals("Test Teacher", result.getBody().name());
    assertTrue(result.getBody().institutionalEmail().endsWith("@test.com"));
  }

  // change password

  @Test
  @DisplayName("Changing the password returns the updated user and gives the session a new id")
  void changePasswordRenewsSession() {
    // Arrange - logged in, and the (fake) service accepts the change
    ChangePasswordRequestDTO change = new ChangePasswordRequestDTO(null, "NewPass123");
    when(userService.changePassword(EMAIL, change)).thenReturn(teacher);
    MockHttpSession session = new MockHttpSession();
    request.setSession(session);
    String idBefore = session.getId();

    // Act
    ResponseEntity<CurrentUserDTO> result = controller.changePassword(change, authenticated, request);

    // Assert
    assertEquals(200, result.getStatusCode().value());
    assertFalse(result.getBody().mustChangePassword());
    assertNotEquals(idBefore, request.getSession(false).getId());
  }

  @Test
  @DisplayName("When the service refuses the change, the session id stays the same")
  void refusedChangeKeepsSession() {
    // Arrange
    ChangePasswordRequestDTO change = new ChangePasswordRequestDTO("WrongPass123", "NewPass123");
    when(userService.changePassword(EMAIL, change))
        .thenThrow(new StudyMatchException(ErrorCode.CURRENT_PASSWORD_INCORRECT));
    MockHttpSession session = new MockHttpSession();
    request.setSession(session);
    String idBefore = session.getId();

    // Act
    assertThrows(StudyMatchException.class, () -> controller.changePassword(change, authenticated, request));

    // Assert
    assertEquals(idBefore, request.getSession(false).getId());
  }

}
