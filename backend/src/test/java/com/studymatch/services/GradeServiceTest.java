package com.studymatch.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.util.ReflectionTestUtils;

import com.studymatch.dtos.GradeResponseDTO;
import com.studymatch.dtos.GradeSheetDTO;
import com.studymatch.enums.AssignmentType;
import com.studymatch.enums.EnrolmentStatus;
import com.studymatch.exceptions.ErrorCode;
import com.studymatch.exceptions.StudyMatchException;
import com.studymatch.models.Assignment;
import com.studymatch.models.Enrolment;
import com.studymatch.models.Grade;
import com.studymatch.models.GroupingRule;
import com.studymatch.models.ModelTestData;
import com.studymatch.models.Student;
import com.studymatch.models.SubjectEdition;
import com.studymatch.repositories.GradeRepository;

class GradeServiceTest {

  private static final Long EDITION_ID = 7L;

  private GradeRepository gradeRepository;
  private SubjectEditionService subjectEditionService;
  private AssignmentService assignmentService;
  private EnrolmentService enrolmentService;
  private GradeService gradeService;

  // what the database would hold
  private final List<Grade> stored = new ArrayList<>();

  private SubjectEdition edition;
  private Assignment project;
  private Assignment exam;
  private Enrolment ana;

  @BeforeEach
  void setUp() {
    gradeRepository = mock(GradeRepository.class);
    subjectEditionService = mock(SubjectEditionService.class);
    assignmentService = mock(AssignmentService.class);
    enrolmentService = mock(EnrolmentService.class);
    gradeService = new GradeService(gradeRepository, subjectEditionService, assignmentService, enrolmentService);

    edition = ModelTestData.subjectEdition();
    project = assignment(1L, "Project", AssignmentType.PROJECT, "0.40");
    exam = assignment(2L, "Exam", AssignmentType.EXAM, "0.60");
    ana = enrolment(1L, "Ana", 1001L);

    when(subjectEditionService.getEdition(EDITION_ID)).thenReturn(edition);
    when(subjectEditionService.getLockedEdition(EDITION_ID)).thenReturn(edition);
    when(assignmentService.getAssignment(EDITION_ID, 1L)).thenReturn(project);
    when(assignmentService.getAssignment(EDITION_ID, 2L)).thenReturn(exam);
    when(assignmentService.listAssignments(edition)).thenReturn(List.of(project, exam));
    when(assignmentService.countAssignments(edition)).thenReturn(2L);
    when(assignmentService.hasFullWeight(edition)).thenReturn(true);
    when(enrolmentService.getEnrolment(EDITION_ID, 1001L)).thenReturn(ana);

    // the repository works on the stored list, like the real one would
    when(gradeRepository.save(any(Grade.class))).thenAnswer(invocation -> {
      Grade grade = invocation.getArgument(0);
      if (!stored.contains(grade)) {
        stored.add(grade);
      }
      return grade;
    });
    when(gradeRepository.findByEnrolmentAndAssignment(any(), any())).thenAnswer(invocation -> stored.stream()
        .filter(g -> g.getEnrolment() == invocation.getArgument(0) && g.getAssignment() == invocation.getArgument(1))
        .findFirst());
    when(gradeRepository.findByEnrolment(any())).thenAnswer(invocation -> stored.stream()
        .filter(g -> g.getEnrolment() == invocation.getArgument(0))
        .toList());
    when(gradeRepository.findByEnrolment_SubjectEditionId(EDITION_ID)).thenAnswer(invocation -> List.copyOf(stored));
  }

  // ids and numbers are given by the database, so they're set by hand here
  private Assignment assignment(Long id, String name, AssignmentType type, String weight) {
    Assignment assignment = new Assignment(edition, name, type, LocalDate.of(2030, 6, 1), new BigDecimal(weight),
        null);
    ReflectionTestUtils.setField(assignment, "id", id);
    return assignment;
  }

  private static Enrolment enrolment(Long id, String name, Long number) {
    Student student = ModelTestData.student(name);
    ReflectionTestUtils.setField(student, "number", number);
    Enrolment enrolment = ModelTestData.enrolment(student);
    ReflectionTestUtils.setField(enrolment, "id", id);
    return enrolment;
  }

  private GradeResponseDTO grade(Assignment assignment, String value) {
    return gradeService.gradeStudent(EDITION_ID, assignment.getId(), 1001L, new BigDecimal(value));
  }

  // grade student

  @Test
  @DisplayName("A first grade is saved and comes back in the student's row, no final grade with one still missing")
  void firstGradeIsSaved() {
    // Act
    GradeResponseDTO response = grade(project, "14.5");

    // Assert
    assertEquals(1, stored.size());
    assertEquals(Map.of(1L, 14.5), response.student().grades());
    assertNull(response.student().finalGrade());
    assertEquals(EnrolmentStatus.IN_PROGRESS, response.student().status());
    assertTrue(response.weightsComplete());
  }

  @Test
  @DisplayName("Grading the last assignment sets the weighted final grade and the status")
  void lastGradeSetsFinalGrade() {
    // Arrange
    grade(project, "14");

    // Act - 14 x 40% + 12 x 60% = 12.8
    GradeResponseDTO response = grade(exam, "12");

    // Assert
    assertEquals(13, response.student().finalGrade());
    assertEquals(EnrolmentStatus.PASSED, response.student().status());
    assertEquals(13, ana.getFinalGrade());
  }

  @ParameterizedTest(name = "{0} and {1} -> {2} {3}")
  @CsvSource({ "9.5, 9.5, 10, PASSED", "9.49, 9.49, 9, FAILED", "20, 20, 20, PASSED", "0, 0, 0, FAILED" })
  @DisplayName("The final grade is rounded half up, so 9.5 passes and 9.49 doesn't")
  void finalGradeRounding(String projectGrade, String examGrade, int finalGrade, EnrolmentStatus status) {
    // Arrange
    grade(project, projectGrade);

    // Act
    GradeResponseDTO response = grade(exam, examGrade);

    // Assert
    assertEquals(finalGrade, response.student().finalGrade());
    assertEquals(status, response.student().status());
  }

  @Test
  @DisplayName("Grading again replaces the grade and recalculates the final grade")
  void regradeRecalculates() {
    // Arrange - 13, passed
    grade(project, "14");
    grade(exam, "12");

    // Act - 14 x 40% + 2 x 60% = 6.8
    GradeResponseDTO response = grade(exam, "2");

    // Assert
    assertEquals(2, stored.size());
    assertEquals(Map.of(1L, 14.0, 2L, 2.0), response.student().grades());
    assertEquals(7, response.student().finalGrade());
    assertEquals(EnrolmentStatus.FAILED, response.student().status());
  }

  @Test
  @DisplayName("Weights under 100%: the grade is saved but there's no final grade, and the response says so")
  void incompleteWeightsNoFinalGrade() {
    // Arrange
    when(assignmentService.hasFullWeight(edition)).thenReturn(false);
    grade(project, "14");

    // Act
    GradeResponseDTO response = grade(exam, "12");

    // Assert
    assertEquals(2, stored.size());
    assertFalse(response.weightsComplete());
    assertNull(response.student().finalGrade());
    assertEquals(EnrolmentStatus.IN_PROGRESS, response.student().status());
  }

  @Test
  @DisplayName("Weights dropping under 100% after a final grade: grading again clears it back to in progress")
  void weightsDropClearsFinalGrade() {
    // Arrange - 13, passed, then a weight changed
    grade(project, "14");
    grade(exam, "12");
    when(assignmentService.hasFullWeight(edition)).thenReturn(false);

    // Act
    GradeResponseDTO response = grade(exam, "15");

    // Assert
    assertNull(response.student().finalGrade());
    assertEquals(EnrolmentStatus.IN_PROGRESS, response.student().status());
    assertNull(ana.getFinalGrade());
  }

  @Test
  @DisplayName("A new assignment after a final grade: grading again clears it, one is still missing")
  void newAssignmentClearsFinalGrade() {
    // Arrange - 13, passed, then a third assignment came
    grade(project, "14");
    grade(exam, "12");
    when(assignmentService.countAssignments(edition)).thenReturn(3L);

    // Act
    GradeResponseDTO response = grade(project, "16");

    // Assert
    assertNull(response.student().finalGrade());
    assertEquals(EnrolmentStatus.IN_PROGRESS, ana.getStatus());
  }

  @Test
  @DisplayName("An assignment of another edition: NOT_FOUND goes through and nothing is saved")
  void unknownAssignment() {
    // Arrange
    when(assignmentService.getAssignment(EDITION_ID, 9L)).thenThrow(new StudyMatchException(ErrorCode.NOT_FOUND));

    // Act
    StudyMatchException e = assertThrows(StudyMatchException.class,
        () -> gradeService.gradeStudent(EDITION_ID, 9L, 1001L, BigDecimal.TEN));

    // Assert
    assertEquals(ErrorCode.NOT_FOUND, e.getCode());
    verify(gradeRepository, never()).save(any());
  }

  @Test
  @DisplayName("A student who isn't enrolled: STUDENT_NOT_ENROLLED goes through and nothing is saved")
  void studentNotEnrolled() {
    // Arrange
    when(enrolmentService.getEnrolment(EDITION_ID, 9999L))
        .thenThrow(new StudyMatchException(ErrorCode.STUDENT_NOT_ENROLLED, Map.of("number", 9999L)));

    // Act
    StudyMatchException e = assertThrows(StudyMatchException.class,
        () -> gradeService.gradeStudent(EDITION_ID, 1L, 9999L, BigDecimal.TEN));

    // Assert
    assertEquals(ErrorCode.STUDENT_NOT_ENROLLED, e.getCode());
    verify(gradeRepository, never()).save(any());
  }

  // grade sheet

  @Test
  @DisplayName("The sheet has a column per assignment and a row per student, each with only their own grades")
  void gradeSheet() {
    // Arrange - the project is group work, the exam isn't
    ReflectionTestUtils.setField(project, "groupingRule", new GroupingRule(2, 3, 4, false));
    Enrolment rui = enrolment(2L, "Rui", 1002L);
    when(enrolmentService.listEnrolments(EDITION_ID)).thenReturn(List.of(ana, rui));
    grade(project, "14");

    // Act
    GradeSheetDTO sheet = gradeService.getGradeSheet(EDITION_ID);

    // Assert
    assertEquals(List.of(new GradeSheetDTO.Column(1L, "Project", AssignmentType.PROJECT, 40, true),
        new GradeSheetDTO.Column(2L, "Exam", AssignmentType.EXAM, 60, false)), sheet.assignments());
    assertEquals(new GradeSheetDTO.Row(1001L, "Ana", "T1", Map.of(1L, 14.0), null, EnrolmentStatus.IN_PROGRESS),
        sheet.students().get(0));
    assertEquals(Map.of(), sheet.students().get(1).grades());
    assertTrue(sheet.weightsComplete());
  }

}
