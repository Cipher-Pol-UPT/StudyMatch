package com.studymatch.services;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import com.studymatch.dtos.ChangePasswordRequestDTO;
import com.studymatch.dtos.ResetPasswordRequestDTO;
import com.studymatch.enums.AccountStatus;
import com.studymatch.exceptions.ErrorCode;
import com.studymatch.exceptions.StudyMatchException;
import com.studymatch.mail.EmailSender;
import com.studymatch.models.Teacher;
import com.studymatch.models.User;
import com.studymatch.repositories.UserRepository;

class UserServiceTest {

  private static final String CURRENT = "Current123";
  private static final String NEW = "NewPass123";

  private UserRepository userRepository;
  private PasswordEncoder passwordEncoder;
  private EmailSender emailSender;
  private UserService userService;
  private Teacher ana;

  @BeforeEach
  void setUp() {
    // fake repository (no db) and fake email sender (no emails), real encoder (cost 4 so it's fast), real service
    userRepository = mock(UserRepository.class);
    passwordEncoder = new BCryptPasswordEncoder(4);
    emailSender = mock(EmailSender.class);
    userService = new UserService(userRepository, passwordEncoder, emailSender);
    ana = new Teacher("ana@example.com", "ana@test.com", "Ana Test", passwordEncoder.encode(CURRENT));
  }

  @Test
  @DisplayName("Email lookup ignores capitals and surrounding spaces")
  void findNormalisesEmail() {
    // Arrange - the fake only knows the lowercase email
    when(userRepository.findByInstitutionalEmail("ana@test.com")).thenReturn(Optional.of(ana));

    // Act
    Optional<User> result = userService.findByInstitutionalEmail("  ANA@Test.com ");

    // Assert
    assertSame(ana, result.orElseThrow());
    verify(userRepository).findByInstitutionalEmail("ana@test.com");
  }

  @Test
  @DisplayName("An unknown email finds nobody")
  void findUnknownEmailIsEmpty() {
    // nothing programmed -> the fake repository returns an empty Optional
    assertTrue(userService.findByInstitutionalEmail("nobody@test.com").isEmpty());
  }

  @Test
  @DisplayName("getByInstitutionalEmail returns the user when found")
  void getReturnsUser() {
    // Arrange
    when(userRepository.findByInstitutionalEmail("ana@test.com")).thenReturn(Optional.of(ana));

    // Act + Assert
    assertSame(ana, userService.getByInstitutionalEmail("ana@test.com"));
  }

  @Test
  @DisplayName("getByInstitutionalEmail throws NOT_FOUND when missing")
  void getMissingUserThrowsNotFound() {
    // Act
    StudyMatchException e = assertThrows(StudyMatchException.class,
        () -> userService.getByInstitutionalEmail("nobody@test.com"));

    // Assert
    assertEquals(ErrorCode.NOT_FOUND, e.getCode());
  }

  // change password

  // ana exists, and saving gives back whatever was saved (like the real repository does)
  private void anaIsInTheSystem(boolean mustChangePassword) {
    ana.setMustChangePassword(mustChangePassword);
    when(userRepository.findByInstitutionalEmail("ana@test.com")).thenReturn(Optional.of(ana));
    when(userRepository.save(any(User.class))).thenAnswer(call -> call.getArgument(0));
  }

  // the code the service threw when asked to change the password
  private ErrorCode changeFails(String currentPassword, String newPassword) {
    StudyMatchException e = assertThrows(StudyMatchException.class,
        () -> userService.changePassword("ana@test.com", new ChangePasswordRequestDTO(currentPassword, newPassword)));
    return e.getCode();
  }

  @Test
  @DisplayName("With the right current password, the new one is stored as a hash")
  void changePasswordStoresNewHash() {
    // Arrange
    anaIsInTheSystem(false);

    // Act
    User result = userService.changePassword("ana@test.com", new ChangePasswordRequestDTO(CURRENT, NEW));

    // Assert - the new one works, the old one doesn't, and it's never kept in plain text
    assertTrue(passwordEncoder.matches(NEW, result.getPassword()));
    assertFalse(passwordEncoder.matches(CURRENT, result.getPassword()));
    assertNotEquals(NEW, result.getPassword());
    verify(userRepository).save(ana);
  }

  @Test
  @DisplayName("A wrong current password is rejected and nothing is saved")
  void wrongCurrentPassword() {
    // Arrange
    anaIsInTheSystem(false);
    String hashBefore = ana.getPassword();

    // Act
    ErrorCode code = changeFails("WrongPass123", NEW);

    // Assert
    assertEquals(ErrorCode.CURRENT_PASSWORD_INCORRECT, code);
    assertEquals(hashBefore, ana.getPassword());
    verify(userRepository, never()).save(any());
  }

  @Test
  @DisplayName("A normal change without the current password counts as wrong")
  void missingCurrentPassword() {
    // Arrange
    anaIsInTheSystem(false);

    // Act + Assert
    assertEquals(ErrorCode.CURRENT_PASSWORD_INCORRECT, changeFails(null, NEW));
  }

  @Test
  @DisplayName("The forced first change doesn't need the current password and opens the gate")
  void forcedChangeWithoutCurrentPassword() {
    // Arrange - still on the one-time password
    anaIsInTheSystem(true);

    // Act
    User result = userService.changePassword("ana@test.com", new ChangePasswordRequestDTO(null, NEW));

    // Assert
    assertTrue(passwordEncoder.matches(NEW, result.getPassword()));
    assertFalse(result.isMustChangePassword());
  }

  @Test
  @DisplayName("The new password can't be the same as the current one")
  void sameAsCurrent() {
    // Arrange
    anaIsInTheSystem(false);

    // Act + Assert
    assertEquals(ErrorCode.NEW_PASSWORD_SAME_AS_CURRENT, changeFails(CURRENT, CURRENT));
  }

  @Test
  @DisplayName("The forced change can't keep the one-time password")
  void forcedChangeCannotReuseOneTimePassword() {
    // Arrange - CURRENT is the one-time password here
    anaIsInTheSystem(true);

    // Act + Assert
    assertEquals(ErrorCode.NEW_PASSWORD_SAME_AS_CURRENT, changeFails(null, CURRENT));
  }

  @Test
  @DisplayName("72 bytes is the most BCrypt takes (boundary: 72 ok)")
  void passwordAtByteLimit() {
    // Arrange - each é is 2 bytes, so 36 of them are exactly 72 bytes (but only 36 characters)
    anaIsInTheSystem(false);
    String atLimit = "é".repeat(36);

    // Act + Assert
    assertDoesNotThrow(() -> userService.changePassword("ana@test.com", new ChangePasswordRequestDTO(CURRENT, atLimit)));
  }

  @Test
  @DisplayName("One byte over 72 is PASSWORD_TOO_LONG instead of a crash (boundary: 73 fails)")
  void passwordOverByteLimit() {
    // Arrange - 72 bytes of é plus one more byte
    anaIsInTheSystem(false);
    String overLimit = "é".repeat(36) + "1";

    // Act + Assert
    assertEquals(ErrorCode.PASSWORD_TOO_LONG, changeFails(CURRENT, overLimit));
    verify(userRepository, never()).save(any());
  }

  // forgot password: asking for a code

  // asks for a code for ana and grabs the one that went in the (fake) email
  private String requestCodeForAna() {
    userService.requestPasswordReset("ana@test.com");
    ArgumentCaptor<String> sentCode = ArgumentCaptor.forClass(String.class);
    verify(emailSender, atLeastOnce()).sendPasswordResetCode(eq("ana@example.com"), sentCode.capture());
    return sentCode.getValue();
  }

  private ErrorCode resetFails(String code, String newPassword) {
    StudyMatchException e = assertThrows(StudyMatchException.class,
        () -> userService.resetPassword(new ResetPasswordRequestDTO("ana@test.com", code, newPassword)));
    return e.getCode();
  }

  @Test
  @DisplayName("Asking for a code emails 6 digits to the personal email and only keeps their hash")
  void requestSendsCodeToPersonalEmail() {
    // Arrange
    anaIsInTheSystem(false);

    // Act
    String code = requestCodeForAna();

    // Assert
    assertTrue(code.matches("\\d{6}"));
    assertNotEquals(code, ana.getResetCodeHash());
    assertTrue(passwordEncoder.matches(code, ana.getResetCodeHash()));
    verify(userRepository).save(ana);
  }

  @Test
  @DisplayName("An unknown email quietly does nothing")
  void requestForUnknownEmail() {
    // Act - no exception either, the controller answers 204 anyway
    assertDoesNotThrow(() -> userService.requestPasswordReset("nobody@test.com"));

    // Assert
    verify(userRepository, never()).save(any());
    verify(emailSender, never()).sendPasswordResetCode(any(), any());
  }

  @Test
  @DisplayName("A blocked account gets no code")
  void requestForBlockedAccount() {
    // Arrange
    anaIsInTheSystem(false);
    ana.setAccountStatus(AccountStatus.BLOCKED);

    // Act
    userService.requestPasswordReset("ana@test.com");

    // Assert
    assertNull(ana.getResetCodeHash());
    verify(emailSender, never()).sendPasswordResetCode(any(), any());
  }

  @Test
  @DisplayName("Asking again replaces the old code")
  void newCodeReplacesOldOne() {
    // Arrange
    anaIsInTheSystem(false);
    String firstCode = requestCodeForAna();
    String secondCode = requestCodeForAna();
    assumeFalse(firstCode.equals(secondCode)); // 1 in a million they're the same, then this test can't say anything

    // Act + Assert - the first one is dead, the second one works
    assertEquals(ErrorCode.RESET_CODE_INVALID, resetFails(firstCode, NEW));
    assertDoesNotThrow(() -> userService.resetPassword(new ResetPasswordRequestDTO("ana@test.com", secondCode, NEW)));
  }

  // forgot password: using the code

  @Test
  @DisplayName("The right code sets the new password, uses the code up and drops the forced change")
  void resetWithRightCode() {
    // Arrange - even someone still on the one-time password can do this
    anaIsInTheSystem(true);
    String code = requestCodeForAna();

    // Act
    userService.resetPassword(new ResetPasswordRequestDTO("ana@test.com", code, NEW));

    // Assert
    assertTrue(passwordEncoder.matches(NEW, ana.getPassword()));
    assertFalse(ana.isMustChangePassword());
    assertNull(ana.getResetCodeHash());
  }

  @Test
  @DisplayName("A code only works once")
  void codeCannotBeReused() {
    // Arrange
    anaIsInTheSystem(false);
    String code = requestCodeForAna();
    userService.resetPassword(new ResetPasswordRequestDTO("ana@test.com", code, NEW));

    // Act + Assert
    assertEquals(ErrorCode.RESET_CODE_INVALID, resetFails(code, "Another123"));
  }

  @Test
  @DisplayName("A wrong code counts as an attempt and changes nothing")
  void wrongCodeCountsAttempt() {
    // Arrange
    anaIsInTheSystem(false);
    String code = requestCodeForAna();
    String hashBefore = ana.getPassword();
    String wrongCode = code.equals("000000") ? "111111" : "000000";

    // Act
    ErrorCode result = resetFails(wrongCode, NEW);

    // Assert
    assertEquals(ErrorCode.RESET_CODE_INVALID, result);
    assertEquals(1, ana.getResetCodeAttempts());
    assertEquals(hashBefore, ana.getPassword());
  }

  @Test
  @DisplayName("After 4 wrong tries the right code still works (boundary: 4)")
  void fourWrongTriesStillAllowed() {
    // Arrange
    anaIsInTheSystem(false);
    String code = requestCodeForAna();
    String wrongCode = code.equals("000000") ? "111111" : "000000";
    for (int i = 0; i < User.MAX_RESET_ATTEMPTS - 1; i++) {
      resetFails(wrongCode, NEW);
    }

    // Act + Assert
    assertDoesNotThrow(() -> userService.resetPassword(new ResetPasswordRequestDTO("ana@test.com", code, NEW)));
  }

  @Test
  @DisplayName("After 5 wrong tries even the right code is refused (boundary: 5)")
  void fiveWrongTriesBurnTheCode() {
    // Arrange
    anaIsInTheSystem(false);
    String code = requestCodeForAna();
    String wrongCode = code.equals("000000") ? "111111" : "000000";
    for (int i = 0; i < User.MAX_RESET_ATTEMPTS; i++) {
      resetFails(wrongCode, NEW);
    }

    // Act + Assert
    assertEquals(ErrorCode.RESET_CODE_INVALID, resetFails(code, NEW));
  }

  @Test
  @DisplayName("An expired code is refused even if it's right")
  void expiredCode() {
    // Arrange - pretend the code was asked for a while ago
    anaIsInTheSystem(false);
    String code = requestCodeForAna();
    ReflectionTestUtils.setField(ana, "resetCodeExpiresAt", LocalDateTime.now().minusMinutes(1));

    // Act + Assert
    assertEquals(ErrorCode.RESET_CODE_INVALID, resetFails(code, NEW));
  }

  @Test
  @DisplayName("Without asking for a code first, any code is refused")
  void noCodeRequested() {
    // Arrange
    anaIsInTheSystem(false);

    // Act + Assert
    assertEquals(ErrorCode.RESET_CODE_INVALID, resetFails("123456", NEW));
  }

  @Test
  @DisplayName("An unknown email gets the same answer as a wrong code")
  void resetForUnknownEmail() {
    StudyMatchException e = assertThrows(StudyMatchException.class,
        () -> userService.resetPassword(new ResetPasswordRequestDTO("nobody@test.com", "123456", NEW)));
    assertEquals(ErrorCode.RESET_CODE_INVALID, e.getCode());
  }

  @Test
  @DisplayName("The right code with a password over 72 bytes is PASSWORD_TOO_LONG")
  void resetWithTooLongPassword() {
    // Arrange
    anaIsInTheSystem(false);
    String code = requestCodeForAna();

    // Act + Assert
    assertEquals(ErrorCode.PASSWORD_TOO_LONG, resetFails(code, "é".repeat(36) + "1"));
  }

}
