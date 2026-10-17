package com.studymatch.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;

import com.studymatch.dtos.StudentProfileDTO;
import com.studymatch.dtos.UpdateProfileRequestDTO;
import com.studymatch.enums.Language;
import com.studymatch.exceptions.ErrorCode;
import com.studymatch.exceptions.StudyMatchException;
import com.studymatch.models.ModelTestData;
import com.studymatch.models.Student;
import com.studymatch.services.StudentService;
import com.studymatch.services.UserService;

class StudentControllerTest {

  private static final String EMAIL = "50001@students.test.com";
  private static final Long NUMBER = 50001L;

  private StudentService studentService;
  private UserService userService;
  private StudentController controller;

  private Student student;
  private Authentication authentication;
  private StudentProfileDTO profile;

  @BeforeEach
  void setUp() {
    studentService = mock(StudentService.class);
    userService = mock(UserService.class);
    controller = new StudentController(studentService, userService);

    student = ModelTestData.student("Test Student");
    ReflectionTestUtils.setField(student, "number", NUMBER);
    authentication = UsernamePasswordAuthenticationToken.authenticated(EMAIL, null, List.of());
    profile = new StudentProfileDTO(NUMBER, "Test Student", null, null, Set.of(), Set.of(),
        Set.of(Language.PORTUGUESE), List.of(), List.of(), null);
    when(userService.getByInstitutionalEmail(EMAIL)).thenReturn(student);
  }

  @Test
  @DisplayName("My profile is the logged in student's profile, seen by themselves")
  void myProfileUsesTheLoggedInStudent() {
    // Arrange
    when(studentService.getProfile(NUMBER, student)).thenReturn(profile);

    // Act
    ResponseEntity<StudentProfileDTO> response = controller.getMyProfile(authentication);

    // Assert
    assertEquals(200, response.getStatusCode().value());
    assertSame(profile, response.getBody());
  }

  @Test
  @DisplayName("Another profile is seen by the logged in user")
  void otherProfileIsSeenByTheLoggedInUser() {
    // Arrange
    when(studentService.getProfile(50002L, student)).thenReturn(profile);

    // Act
    ResponseEntity<StudentProfileDTO> response = controller.getProfile(50002L, authentication);

    // Assert
    assertEquals(200, response.getStatusCode().value());
    assertSame(profile, response.getBody());
  }

  @Test
  @DisplayName("Editing my profile changes the logged in student's profile")
  void editingUsesTheLoggedInStudent() {
    // Arrange
    UpdateProfileRequestDTO changes = new UpdateProfileRequestDTO(null, "Test about me", Set.of(), Set.of(),
        Set.of(Language.PORTUGUESE));
    when(studentService.updateProfile(NUMBER, changes)).thenReturn(profile);

    // Act
    ResponseEntity<StudentProfileDTO> response = controller.updateMyProfile(changes, authentication);

    // Assert
    assertEquals(200, response.getStatusCode().value());
    assertSame(profile, response.getBody());
  }

  @Test
  @DisplayName("A logged in user that no longer exists gets NOT_FOUND")
  void missingUserGetsNotFound() {
    // Arrange
    when(userService.getByInstitutionalEmail(EMAIL)).thenThrow(new StudyMatchException(ErrorCode.NOT_FOUND));

    // Act
    StudyMatchException e = assertThrows(StudyMatchException.class, () -> controller.getMyProfile(authentication));

    // Assert
    assertEquals(ErrorCode.NOT_FOUND, e.getCode());
    verify(studentService, never()).getProfile(any(), any());
  }

}
