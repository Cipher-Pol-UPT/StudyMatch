package com.studymatch.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.test.util.ReflectionTestUtils;

import com.studymatch.enums.AccountStatus;
import com.studymatch.models.Degree;
import com.studymatch.models.Student;
import com.studymatch.models.Teacher;
import com.studymatch.services.UserService;

class StudyMatchUserDetailsServiceTest {

  private UserService userService;
  private StudyMatchUserDetailsService userDetailsService;
  private Teacher teacher;

  @BeforeEach
  void setUp() {
    userService = mock(UserService.class);
    userDetailsService = new StudyMatchUserDetailsService(userService);
    teacher = new Teacher("t@example.com", "teacher@test.com", "Test Teacher", "$2a$10$fakehash");
  }

  private Set<String> authoritiesOf(UserDetails userDetails) {
    return userDetails.getAuthorities().stream()
        .map(GrantedAuthority::getAuthority)
        .collect(Collectors.toSet());
  }

  @Test
  @DisplayName("A found user is handed to spring with email, hash and role")
  void loadsUserDetails() {
    // Arrange
    when(userService.findByInstitutionalEmail("teacher@test.com")).thenReturn(Optional.of(teacher));

    // Act
    UserDetails userDetails = userDetailsService.loadUserByUsername("teacher@test.com");

    // Assert
    assertEquals("teacher@test.com", userDetails.getUsername());
    assertEquals("$2a$10$fakehash", userDetails.getPassword());
    assertEquals(Set.of("ROLE_TEACHER"), authoritiesOf(userDetails));
  }

  @Test
  @DisplayName("The role comes from the user type")
  void studentGetsStudentRole() {
    // Arrange
    Student student = new Student("s@example.com", "Test Student", "hash", new Degree("Test Degree"), 1);
    ReflectionTestUtils.setField(student, "number", 50003L);
    student.generateInstitutionalEmail("test.com");
    when(userService.findByInstitutionalEmail("50003@students.test.com")).thenReturn(Optional.of(student));

    // Act
    UserDetails userDetails = userDetailsService.loadUserByUsername("50003@students.test.com");

    // Assert
    assertEquals(Set.of("ROLE_STUDENT"), authoritiesOf(userDetails));
  }

  @Test
  @DisplayName("An unknown email is reported as not found")
  void unknownEmailThrows() {
    // nothing programmed -> userService finds nobody
    assertThrows(UsernameNotFoundException.class, () -> userDetailsService.loadUserByUsername("nobody@test.com"));
  }

  @Test
  @DisplayName("Blocked users are still loaded, the blocked check happens after the password")
  void blockedUserIsNotLockedHere() {
    // Arrange
    teacher.setAccountStatus(AccountStatus.BLOCKED);
    when(userService.findByInstitutionalEmail("teacher@test.com")).thenReturn(Optional.of(teacher));

    // Act
    UserDetails userDetails = userDetailsService.loadUserByUsername("teacher@test.com");

    // Assert - locking here would tell anyone the account is blocked before checking the password
    assertTrue(userDetails.isAccountNonLocked());
  }

}
