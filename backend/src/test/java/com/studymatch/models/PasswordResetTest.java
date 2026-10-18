package com.studymatch.models;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class PasswordResetTest {

  private Teacher user;

  @BeforeEach
  void setUp() {
    user = new Teacher("t@example.com", "teacher@test.com", "Test Teacher", "hash");
  }

  @Test
  @DisplayName("Nobody has a reset code until they ask for one")
  void noCodeByDefault() {
    assertFalse(user.hasActiveResetCode());
  }

  @Test
  @DisplayName("A new code is valid for 15 minutes from now")
  void codeLastsFifteenMinutes() {
    // Act
    user.startPasswordReset("code-hash");

    // Assert - a couple of seconds of slack for the test itself running
    Duration validFor = Duration.between(LocalDateTime.now(), user.getResetCodeExpiresAt());
    assertTrue(Math.abs(validFor.minusMinutes(User.RESET_CODE_VALID_MINUTES).toSeconds()) <= 2);
    assertTrue(user.hasActiveResetCode());
  }

  @Test
  @DisplayName("One minute before the end the code still works (boundary)")
  void activeJustBeforeExpiry() {
    // Arrange
    user.startPasswordReset("code-hash");
    ReflectionTestUtils.setField(user, "resetCodeExpiresAt", LocalDateTime.now().plusMinutes(1));

    // Act + Assert
    assertTrue(user.hasActiveResetCode());
  }

  @Test
  @DisplayName("One minute after the end the code is dead (boundary)")
  void expiredJustAfterExpiry() {
    // Arrange
    user.startPasswordReset("code-hash");
    ReflectionTestUtils.setField(user, "resetCodeExpiresAt", LocalDateTime.now().minusMinutes(1));

    // Act + Assert
    assertFalse(user.hasActiveResetCode());
  }

  @Test
  @DisplayName("Asking again resets the attempt count")
  void newCodeResetsAttempts() {
    // Arrange - two wrong tries on the old code
    user.startPasswordReset("old-hash");
    user.registerFailedResetAttempt();
    user.registerFailedResetAttempt();

    // Act
    user.startPasswordReset("new-hash");

    // Assert
    assertEquals(0, user.getResetCodeAttempts());
    assertEquals("new-hash", user.getResetCodeHash());
  }

  @Test
  @DisplayName("Clearing removes everything about the code")
  void clearRemovesCode() {
    // Arrange
    user.startPasswordReset("code-hash");
    user.registerFailedResetAttempt();

    // Act
    user.clearPasswordReset();

    // Assert
    assertNull(user.getResetCodeHash());
    assertNull(user.getResetCodeExpiresAt());
    assertEquals(0, user.getResetCodeAttempts());
    assertFalse(user.hasActiveResetCode());
  }

}
