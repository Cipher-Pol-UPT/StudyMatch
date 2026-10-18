package com.studymatch.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.studymatch.models.Enrolment;
import com.studymatch.models.ModelTestData;
import com.studymatch.models.Student;
import com.studymatch.repositories.EnrolmentRepository;

class EnrolmentServiceTest {

  private EnrolmentRepository enrolmentRepository;
  private LessonService lessonService;
  private AbsenceService absenceService;
  private EnrolmentService enrolmentService;

  private Student student;

  @BeforeEach
  void setUp() {
    enrolmentRepository = mock(EnrolmentRepository.class);
    lessonService = mock(LessonService.class);
    absenceService = mock(AbsenceService.class);
    enrolmentService = new EnrolmentService(enrolmentRepository, lessonService, absenceService);

    student = ModelTestData.student("Test Student");
  }

  @Test
  @DisplayName("Attendance is the share of lessons without an unjustified absence")
  void attendanceCountsUnjustifiedAbsencesOnly() {
    // Arrange
    Enrolment enrolment = enrolmentWith(20, 5);
    when(enrolmentRepository.findByStudent(student)).thenReturn(List.of(enrolment));

    // Act
    Double attendance = enrolmentService.getAttendanceRate(student);

    // Assert
    assertEquals(75.0, attendance);
  }

  @Test
  @DisplayName("Attendance adds up the lessons of every enrolment")
  void attendanceAddsUpEveryEnrolment() {
    // Arrange
    Enrolment first = enrolmentWith(10, 0);
    Enrolment second = enrolmentWith(30, 4);
    when(enrolmentRepository.findByStudent(student)).thenReturn(List.of(first, second));

    // Act
    Double attendance = enrolmentService.getAttendanceRate(student);

    // Assert
    assertEquals(90.0, attendance);
  }

  @Test
  @DisplayName("Attendance with no absences is 100%")
  void attendanceWithoutAbsencesIsFull() {
    // Arrange
    Enrolment enrolment = enrolmentWith(12, 0);
    when(enrolmentRepository.findByStudent(student)).thenReturn(List.of(enrolment));

    // Act
    Double attendance = enrolmentService.getAttendanceRate(student);

    // Assert
    assertEquals(100.0, attendance);
  }

  @Test
  @DisplayName("There is no attendance before the first lesson")
  void noLessonsMeansNoAttendance() {
    // Arrange
    Enrolment enrolment = enrolmentWith(0, 0);
    when(enrolmentRepository.findByStudent(student)).thenReturn(List.of(enrolment));

    // Act
    Double attendance = enrolmentService.getAttendanceRate(student);

    // Assert
    assertNull(attendance);
  }

  @Test
  @DisplayName("A student without enrolments has no attendance")
  void noEnrolmentsMeansNoAttendance() {
    // Act
    Double attendance = enrolmentService.getAttendanceRate(student);

    // Assert
    assertNull(attendance);
  }

  @Test
  @DisplayName("A student with enrolments has academic history")
  void enrolmentsMeanAcademicHistory() {
    // Arrange
    when(enrolmentRepository.existsByStudent(student)).thenReturn(true);

    // Act + Assert
    assertTrue(enrolmentService.hasAcademicHistory(student));
  }

  @Test
  @DisplayName("A student without enrolments has no academic history")
  void noEnrolmentsMeansNoAcademicHistory() {
    // Act + Assert
    assertFalse(enrolmentService.hasAcademicHistory(student));
  }

  @Test
  @DisplayName("A teacher of one of the student's subjects teaches the student")
  void teacherOfTheirSubjectTeachesThem() {
    // Arrange
    when(enrolmentRepository.existsByStudentAndSubjectEdition_Teachers_Number(student, 50003L)).thenReturn(true);

    // Act + Assert
    assertTrue(enrolmentService.isTaughtBy(student, 50003L));
  }

  @Test
  @DisplayName("A teacher of none of the student's subjects doesn't teach the student")
  void otherTeacherDoesNotTeachThem() {
    // Act + Assert
    assertFalse(enrolmentService.isTaughtBy(student, 50003L));
  }

  private Enrolment enrolmentWith(long lessons, long unjustifiedAbsences) {
    Enrolment enrolment = ModelTestData.enrolment(student);
    when(lessonService.countLessons(enrolment.getClassSection())).thenReturn(lessons);
    when(absenceService.countUnjustifiedAbsences(enrolment)).thenReturn(unjustifiedAbsences);
    return enrolment;
  }

}
