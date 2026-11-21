package com.studymatch.models;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class ChatAccessTest {

  static Stream<User> chatAccounts() {
    return Stream.of(ModelTestData.student("Test Student"), ModelTestData.teacher());
  }

  static Stream<User> nonChatAccounts() {
    return Stream.of(
        new Staff("staff@example.com", "staff@test.com", "Test Staff", "hash"),
        new Admin("admin@example.com", "admin@test.com", "Test Admin", "hash"));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("chatAccounts")
  @DisplayName("Students and Teachers take part in chats (GR8)")
  void studentsAndTeachersChat(User user) {
    // Act + Assert
    assertTrue(user.canChat());
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("nonChatAccounts")
  @DisplayName("Staff and Admin don't (GR8)")
  void staffAndAdminDont(User user) {
    // Act + Assert
    assertFalse(user.canChat());
  }

  @Test
  @DisplayName("A Student blocked by an Admin is out of the chats until unblocked")
  void blockedStudentDoesnt() {
    // Arrange
    Student student = ModelTestData.student("Test Student");

    // Act
    student.block("test reason");

    // Assert
    assertFalse(student.canChat());
    student.unblock();
    assertTrue(student.canChat());
  }

}
