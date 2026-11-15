package com.studymatch.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.studymatch.enums.TutorApplicationStatus;
import com.studymatch.models.ModelTestData;
import com.studymatch.models.Student;
import com.studymatch.models.TutorApplication;
import com.studymatch.repositories.TutorApplicationRepository;

class TutorApplicationServiceTest {

  private static final Long STUDENT_NUMBER = 1L;

  private TutorApplicationRepository tutorApplicationRepository;
  private TutorApplicationService tutorApplicationService;

  private Student student;

  @BeforeEach
  void setUp() {
    tutorApplicationRepository = mock(TutorApplicationRepository.class);
    tutorApplicationService = new TutorApplicationService(tutorApplicationRepository);
    student = ModelTestData.student("Test Student");
  }

  @Test
  @DisplayName("Blocking a student cancels their pending applications and says how many")
  void cancelPendingApplications() {
    // Arrange
    TutorApplication first = new TutorApplication(student, ModelTestData.subject(), "test motivation");
    TutorApplication second = new TutorApplication(student, ModelTestData.subject(), "test motivation");
    when(tutorApplicationRepository.findByStudentNumberAndStatus(STUDENT_NUMBER, TutorApplicationStatus.PENDING))
        .thenReturn(List.of(first, second));

    // Act
    int cancelled = tutorApplicationService.cancelPending(STUDENT_NUMBER);

    // Assert
    assertEquals(2, cancelled);
    assertEquals(TutorApplicationStatus.CANCELLED, first.getStatus());
    assertEquals(TutorApplicationStatus.CANCELLED, second.getStatus());
    verify(tutorApplicationRepository).saveAll(List.of(first, second));
  }

  @Test
  @DisplayName("A student without pending applications has nothing cancelled")
  void noPendingApplications() {
    // Act - the repository finds nothing by default
    int cancelled = tutorApplicationService.cancelPending(STUDENT_NUMBER);

    // Assert
    assertEquals(0, cancelled);
  }

  @Test
  @DisplayName("Pending applications are counted without changing them")
  void countPendingApplications() {
    // Arrange
    TutorApplication application = new TutorApplication(student, ModelTestData.subject(), "test motivation");
    when(tutorApplicationRepository.findByStudentNumberAndStatus(STUDENT_NUMBER, TutorApplicationStatus.PENDING))
        .thenReturn(List.of(application));

    // Act
    int pending = tutorApplicationService.countPending(STUDENT_NUMBER);

    // Assert
    assertEquals(1, pending);
    assertEquals(TutorApplicationStatus.PENDING, application.getStatus());
  }

}
