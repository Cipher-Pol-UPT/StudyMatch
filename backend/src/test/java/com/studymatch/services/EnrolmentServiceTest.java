package com.studymatch.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.studymatch.dtos.EnrolmentResultDTO;
import com.studymatch.dtos.EnrolmentResultDTO.Result;
import com.studymatch.enums.EnrolmentStatus;
import com.studymatch.exceptions.ErrorCode;
import com.studymatch.exceptions.StudyMatchException;
import com.studymatch.models.Enrolment;
import com.studymatch.models.ModelTestData;
import com.studymatch.models.Student;
import com.studymatch.models.SubjectEdition;
import com.studymatch.repositories.EnrolmentRepository;
import com.studymatch.utils.EnrolmentCsvReader;

class EnrolmentServiceTest {

  private EnrolmentRepository enrolmentRepository;
  private LessonService lessonService;
  private AbsenceService absenceService;
  private SubjectEditionService subjectEditionService;
  private EnrolmentService enrolmentService;

  private Student student;

  @BeforeEach
  void setUp() {
    enrolmentRepository = mock(EnrolmentRepository.class);
    lessonService = mock(LessonService.class);
    absenceService = mock(AbsenceService.class);
    subjectEditionService = mock(SubjectEditionService.class);
    enrolmentService = new EnrolmentService(enrolmentRepository, lessonService, absenceService, subjectEditionService);

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

  // picking students of one edition (separations)

  @Test
  @DisplayName("A student enrolled in the edition is found by number")
  void enrolledStudentIsFound() {
    // Arrange
    when(enrolmentRepository.findBySubjectEditionIdAndStudent_Number(7L, 1001L))
        .thenReturn(Optional.of(ModelTestData.enrolment(student)));

    // Act + Assert
    assertSame(student, enrolmentService.getEnrolledStudent(7L, 1001L));
  }

  @Test
  @DisplayName("A student who isn't in the edition gets STUDENT_NOT_ENROLLED with their number")
  void studentNotInEditionIsRefused() {
    // Act
    StudyMatchException e = assertThrows(StudyMatchException.class, () -> enrolmentService.getEnrolledStudent(7L, 1001L));

    // Assert
    assertEquals(ErrorCode.STUDENT_NOT_ENROLLED, e.getCode());
    assertEquals(Map.of("number", 1001L), e.getParams());
  }

  // enrol students

  private SubjectEdition editionWithT1() {
    SubjectEdition edition = ModelTestData.subjectEdition();
    edition.addClassSection("T1");
    when(subjectEditionService.getLockedCurrentEdition(7L)).thenReturn(edition);
    return edition;
  }

  @Test
  @DisplayName("A student is enrolled in the chosen class, spaces around the class code don't matter")
  void studentIsEnrolled() {
    // Arrange
    editionWithT1();

    // Act
    List<EnrolmentResultDTO> results = enrolmentService.enrolStudents(7L, " T1 ", List.of(1001L),
        Map.of(1001L, student));

    // Assert
    assertEquals(List.of(EnrolmentResultDTO.of(1001L, "Test Student", Result.ENROLLED)), results);
    ArgumentCaptor<Enrolment> saved = ArgumentCaptor.forClass(Enrolment.class);
    verify(enrolmentRepository).save(saved.capture());
    assertSame(student, saved.getValue().getStudent());
    assertEquals("T1", saved.getValue().getClassSection().getCode());
  }

  @Test
  @DisplayName("Every number gets a result in the order sent: unknown, already enrolled and already passed are skipped")
  void skippedStudentsAreListed() {
    // Arrange
    SubjectEdition edition = editionWithT1();
    Student enrolled = ModelTestData.student("Enrolled Student");
    Student passed = ModelTestData.student("Passed Student");
    when(enrolmentRepository.existsByStudentAndSubjectEdition(enrolled, edition)).thenReturn(true);
    when(enrolmentRepository.existsByStudentAndSubjectEdition_SubjectAndStatus(passed, edition.getSubject(),
        EnrolmentStatus.PASSED)).thenReturn(true);

    // Act
    List<EnrolmentResultDTO> results = enrolmentService.enrolStudents(7L, "T1", List.of(9999L, 1002L, 1003L),
        Map.of(1002L, enrolled, 1003L, passed));

    // Assert
    assertEquals(List.of(
        EnrolmentResultDTO.of(9999L, null, Result.NOT_FOUND),
        EnrolmentResultDTO.of(1002L, "Enrolled Student", Result.ALREADY_ENROLLED),
        EnrolmentResultDTO.of(1003L, "Passed Student", Result.ALREADY_PASSED)), results);
    verify(enrolmentRepository, never()).save(any());
  }

  @Test
  @DisplayName("A number sent twice is only enrolled once")
  void repeatedNumberCountsOnce() {
    // Arrange
    editionWithT1();

    // Act
    List<EnrolmentResultDTO> results = enrolmentService.enrolStudents(7L, "T1", List.of(1001L, 1001L),
        Map.of(1001L, student));

    // Assert
    assertEquals(1, results.size());
    verify(enrolmentRepository, times(1)).save(any());
  }

  @Test
  @DisplayName("A class the edition doesn't have gets CLASS_SECTION_NOT_IN_SUBJECT_EDITION and nobody is enrolled")
  void unknownClassIsRefused() {
    // Arrange
    editionWithT1();

    // Act
    StudyMatchException e = assertThrows(StudyMatchException.class,
        () -> enrolmentService.enrolStudents(7L, "T9", List.of(1001L), Map.of(1001L, student)));

    // Assert
    assertEquals(ErrorCode.CLASS_SECTION_NOT_IN_SUBJECT_EDITION, e.getCode());
    verify(enrolmentRepository, never()).save(any());
  }

  @Test
  @DisplayName("An edition that isn't current: SUBJECT_EDITION_NOT_CURRENT goes through and nobody is enrolled")
  void notCurrentEditionIsRefused() {
    // Arrange
    when(subjectEditionService.getLockedCurrentEdition(7L))
        .thenThrow(new StudyMatchException(ErrorCode.SUBJECT_EDITION_NOT_CURRENT));

    // Act
    StudyMatchException e = assertThrows(StudyMatchException.class,
        () -> enrolmentService.enrolStudents(7L, "T1", List.of(1001L), Map.of(1001L, student)));

    // Assert
    assertEquals(ErrorCode.SUBJECT_EDITION_NOT_CURRENT, e.getCode());
    verify(enrolmentRepository, never()).save(any());
  }

  @Test
  @DisplayName("CSV: every row gets its result and row, bad and repeated numbers are listed, only the good one is enrolled")
  void importListsEveryRow() {
    // Arrange
    editionWithT1();
    List<EnrolmentCsvReader.Row> rows = List.of(new EnrolmentCsvReader.Row(2, 1001L),
        new EnrolmentCsvReader.Row(3, null), new EnrolmentCsvReader.Row(4, 1001L), new EnrolmentCsvReader.Row(5, 9999L));

    // Act
    List<EnrolmentResultDTO> results = enrolmentService.importEnrolments(7L, "T1", rows, Map.of(1001L, student));

    // Assert
    assertEquals(List.of(
        new EnrolmentResultDTO(2, 1001L, "Test Student", Result.ENROLLED),
        new EnrolmentResultDTO(3, null, null, Result.INVALID_NUMBER),
        new EnrolmentResultDTO(4, 1001L, null, Result.DUPLICATE_IN_FILE),
        new EnrolmentResultDTO(5, 9999L, null, Result.NOT_FOUND)), results);
    verify(enrolmentRepository, times(1)).save(any());
  }

  private Enrolment enrolmentWith(long lessons, long unjustifiedAbsences) {
    Enrolment enrolment = ModelTestData.enrolment(student);
    when(lessonService.countLessons(enrolment.getClassSection())).thenReturn(lessons);
    when(absenceService.countUnjustifiedAbsences(enrolment)).thenReturn(unjustifiedAbsences);
    return enrolment;
  }

}
