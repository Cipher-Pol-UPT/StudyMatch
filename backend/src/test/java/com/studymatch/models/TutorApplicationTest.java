package com.studymatch.models;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.studymatch.enums.TutorApplicationStatus;
import com.studymatch.exceptions.ErrorCode;
import com.studymatch.exceptions.StudyMatchException;

class TutorApplicationTest {

  private Student student;
  private Subject subject;
  private Teacher teacher;
  private TutorApplication application;

  @BeforeEach
  void setUp() {
    student = ModelTestData.student("Test Student");
    subject = ModelTestData.subject();
    teacher = ModelTestData.teacher();
    application = new TutorApplication(student, subject, "test motivation");
  }

  @Test
  @DisplayName("A new application is pending and not decided yet")
  void newApplicationIsPending() {
    assertEquals(TutorApplicationStatus.PENDING, application.getStatus());
    assertNotNull(application.getSubmittedAt());
    assertNull(application.getDecidedAt());
    assertNull(application.getDecidedBy());
  }

  @Test
  @DisplayName("Approving creates a tutor role for the same student and subject")
  void approveCreatesTutorRole() {
    // Act
    TutorRole tutorRole = application.approve(teacher);

    // Assert
    assertEquals(TutorApplicationStatus.APPROVED, application.getStatus());
    assertSame(teacher, application.getDecidedBy());
    assertNotNull(application.getDecidedAt());
    assertSame(student, tutorRole.getStudent());
    assertSame(subject, tutorRole.getSubject());
  }

  @Test
  @DisplayName("The tutor role was approved when the application was decided")
  void tutorRoleApprovedAtIsDecisionTime() {
    // Act
    TutorRole tutorRole = application.approve(teacher);

    // Assert
    assertEquals(application.getDecidedAt(), tutorRole.getApprovedAt());
  }

  @Test
  @DisplayName("Rejecting stores the status and the reason")
  void rejectStoresReason() {
    // Act
    application.reject(teacher, "grade too low");

    // Assert
    assertEquals(TutorApplicationStatus.REJECTED, application.getStatus());
    assertEquals("grade too low", application.getRejectionReason());
    assertSame(teacher, application.getDecidedBy());
  }

  @Test
  @DisplayName("A rejection reason is optional")
  void rejectWithoutReason() {
    // Act
    application.reject(teacher, null);

    // Assert
    assertEquals(TutorApplicationStatus.REJECTED, application.getStatus());
    assertNull(application.getRejectionReason());
  }

  // an application can only be decided once

  @Test
  @DisplayName("An application can't be approved twice")
  void approveTwiceIsRejected() {
    // Arrange
    application.approve(teacher);

    // Act
    StudyMatchException e = assertThrows(StudyMatchException.class, () -> application.approve(teacher));

    // Assert
    assertEquals(ErrorCode.APPLICATION_ALREADY_DECIDED, e.getCode());
  }

  @Test
  @DisplayName("An approved application can't be rejected")
  void rejectAfterApproveIsRejected() {
    // Arrange
    application.approve(teacher);

    // Act
    StudyMatchException e = assertThrows(StudyMatchException.class, () -> application.reject(teacher, "changed my mind"));

    // Assert
    assertEquals(ErrorCode.APPLICATION_ALREADY_DECIDED, e.getCode());
  }

  @Test
  @DisplayName("A rejected application can't be approved")
  void approveAfterRejectIsRejected() {
    // Arrange
    application.reject(teacher, null);

    // Act
    StudyMatchException e = assertThrows(StudyMatchException.class, () -> application.approve(teacher));

    // Assert
    assertEquals(ErrorCode.APPLICATION_ALREADY_DECIDED, e.getCode());
  }

}
