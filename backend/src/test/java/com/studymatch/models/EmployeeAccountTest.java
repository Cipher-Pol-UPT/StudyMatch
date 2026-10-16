package com.studymatch.models;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.studymatch.enums.Role;

class EmployeeAccountTest {

  @Test
  @DisplayName("Each employee type has its own role")
  void eachEmployeeTypeHasItsRole() {
    assertEquals(Role.ADMIN, new Admin("a@example.com", "admin@test.com", "Test Admin", "hash").getRole());
    assertEquals(Role.TEACHER, new Teacher("t@example.com", "teacher@test.com", "Test Teacher", "hash").getRole());
    assertEquals(Role.STAFF, new Staff("s@example.com", "staff@test.com", "Test Staff", "hash").getRole());
  }

  @Test
  @DisplayName("The institutional email given at creation is kept")
  void institutionalEmailIsKept() {
    // Act
    Teacher teacher = new Teacher("t@example.com", "joao.silva@test.com", "Test Teacher", "hash");

    // Assert
    assertEquals("joao.silva@test.com", teacher.getInstitutionalEmail());
  }

  @Test
  @DisplayName("New employee accounts have to change their one-time password")
  void newEmployeesMustChangePassword() {
    assertTrue(new Staff("s@example.com", "staff@test.com", "Test Staff", "hash").isMustChangePassword());
  }

}
