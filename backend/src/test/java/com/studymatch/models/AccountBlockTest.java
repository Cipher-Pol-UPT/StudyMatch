package com.studymatch.models;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import com.studymatch.enums.AccountStatus;
import com.studymatch.exceptions.ErrorCode;
import com.studymatch.exceptions.StudyMatchException;

class AccountBlockTest {

  static Stream<User> blockableAccounts() {
    return Stream.of(
        ModelTestData.student("Test Student"),
        ModelTestData.teacher(),
        new Staff("staff@example.com", "staff@test.com", "Test Staff", "hash"));
  }

  private static Admin admin() {
    return new Admin("admin@example.com", "admin@test.com", "Test Admin", "hash");
  }

  @Test
  @DisplayName("A new account is active, with no block reason")
  void newAccountIsActive() {
    // Arrange
    User user = ModelTestData.student("Test Student");

    // Act + Assert
    assertFalse(user.isBlocked());
    assertNull(user.getBlockReason());
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("blockableAccounts")
  @DisplayName("Students, teachers and staff can be blocked, and the reason is kept")
  void blockStoresStatusAndReason(User user) {
    // Act
    user.block("test reason");

    // Assert
    assertTrue(user.isBlocked());
    assertEquals(AccountStatus.BLOCKED, user.getAccountStatus());
    assertEquals("test reason", user.getBlockReason());
  }

  @Test
  @DisplayName("An admin can't be blocked")
  void adminCantBeBlocked() {
    // Arrange
    Admin admin = admin();

    // Act
    StudyMatchException e = assertThrows(StudyMatchException.class, () -> admin.block("test reason"));

    // Assert
    assertEquals(ErrorCode.ADMIN_CANNOT_BE_BLOCKED, e.getCode());
    assertFalse(admin.isBlocked());
  }

  @Test
  @DisplayName("A blocked account can't be blocked again, the first reason stays")
  void blockTwiceIsRejected() {
    // Arrange
    User user = ModelTestData.teacher();
    user.block("first reason");

    // Act
    StudyMatchException e = assertThrows(StudyMatchException.class, () -> user.block("second reason"));

    // Assert
    assertEquals(ErrorCode.ACCOUNT_ALREADY_BLOCKED, e.getCode());
    assertEquals("first reason", user.getBlockReason());
  }

  @Test
  @DisplayName("Checking an active account that isn't an admin passes and changes nothing")
  void checkActiveAccountPasses() {
    // Arrange
    User user = ModelTestData.student("Test Student");

    // Act + Assert
    assertDoesNotThrow(user::checkCanBeBlocked);
    assertFalse(user.isBlocked());
  }

  @Test
  @DisplayName("Checking an admin gives the same code as blocking one")
  void checkAdminFails() {
    // Act
    StudyMatchException e = assertThrows(StudyMatchException.class, admin()::checkCanBeBlocked);

    // Assert
    assertEquals(ErrorCode.ADMIN_CANNOT_BE_BLOCKED, e.getCode());
  }

  // unblock account (#33)

  @ParameterizedTest(name = "{0}")
  @MethodSource("blockableAccounts")
  @DisplayName("Unblocking makes the account active again and the reason is cleared")
  void unblockRestoresAccess(User user) {
    // Arrange
    user.block("test reason");

    // Act
    user.unblock();

    // Assert
    assertFalse(user.isBlocked());
    assertEquals(AccountStatus.ACTIVE, user.getAccountStatus());
    assertNull(user.getBlockReason());
  }

  @Test
  @DisplayName("Unblocking an active account gives ACCOUNT_NOT_BLOCKED")
  void unblockActiveAccountIsRejected() {
    // Arrange
    User user = ModelTestData.teacher();

    // Act
    StudyMatchException e = assertThrows(StudyMatchException.class, user::unblock);

    // Assert
    assertEquals(ErrorCode.ACCOUNT_NOT_BLOCKED, e.getCode());
  }

  @Test
  @DisplayName("An unblocked account can be blocked again, with the new reason")
  void unblockedAccountCanBeBlockedAgain() {
    // Arrange
    User user = ModelTestData.teacher();
    user.block("first reason");
    user.unblock();

    // Act
    user.block("second reason");

    // Assert
    assertTrue(user.isBlocked());
    assertEquals("second reason", user.getBlockReason());
  }

}
