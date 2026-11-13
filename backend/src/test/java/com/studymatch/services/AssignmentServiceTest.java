package com.studymatch.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import com.studymatch.dtos.AssignmentResponseDTO;
import com.studymatch.dtos.CreateAssignmentRequestDTO;
import com.studymatch.enums.AssignmentType;
import com.studymatch.exceptions.ErrorCode;
import com.studymatch.exceptions.StudyMatchException;
import com.studymatch.models.Assignment;
import com.studymatch.models.ModelTestData;
import com.studymatch.models.Student;
import com.studymatch.models.SubjectEdition;
import com.studymatch.repositories.AssignmentRepository;

class AssignmentServiceTest {

  private static final Long EDITION_ID = 7L;
  private static final LocalDate DATE = LocalDate.of(2030, 6, 1);

  private AssignmentRepository assignmentRepository;
  private SubjectEditionService subjectEditionService;
  private EnrolmentService enrolmentService;
  private AssignmentService assignmentService;

  private SubjectEdition edition;
  private Student ana;
  private Student rui;

  @BeforeEach
  void setUp() {
    assignmentRepository = mock(AssignmentRepository.class);
    subjectEditionService = mock(SubjectEditionService.class);
    enrolmentService = mock(EnrolmentService.class);
    assignmentService = new AssignmentService(assignmentRepository, subjectEditionService, enrolmentService);

    edition = ModelTestData.subjectEdition();
    ana = numbered("Ana", 1001L);
    rui = numbered("Rui", 1002L);
    when(subjectEditionService.getLockedEdition(EDITION_ID)).thenReturn(edition);
    when(assignmentRepository.sumWeightBySubjectEdition(edition)).thenReturn(BigDecimal.ZERO);
    when(enrolmentService.getEnrolledStudent(EDITION_ID, 1001L)).thenReturn(ana);
    when(enrolmentService.getEnrolledStudent(EDITION_ID, 1002L)).thenReturn(rui);

    // save returns what it was given
    when(assignmentRepository.save(any(Assignment.class))).thenAnswer(invocation -> invocation.getArgument(0));
  }

  // numbers are given by the database, so they're set by hand here
  private static Student numbered(String name, Long number) {
    Student student = ModelTestData.student(name);
    ReflectionTestUtils.setField(student, "number", number);
    return student;
  }

  // the edition's assignments already add up to this
  private void alreadyUsed(String total) {
    when(assignmentRepository.sumWeightBySubjectEdition(edition)).thenReturn(new BigDecimal(total));
  }

  private static CreateAssignmentRequestDTO exam(int weight) {
    return new CreateAssignmentRequestDTO(" Final Exam ", AssignmentType.EXAM, DATE, weight, null);
  }

  private static CreateAssignmentRequestDTO project(AssignmentType type, int minSize,
      CreateAssignmentRequestDTO.Separation... separations) {
    return new CreateAssignmentRequestDTO("Project", type, DATE, 40,
        new CreateAssignmentRequestDTO.Grouping(minSize, 3, 4, true, List.of(separations)));
  }

  private static CreateAssignmentRequestDTO.Separation pair(Long first, Long second) {
    return new CreateAssignmentRequestDTO.Separation(first, second);
  }

  private Assignment savedAssignment() {
    ArgumentCaptor<Assignment> saved = ArgumentCaptor.forClass(Assignment.class);
    verify(assignmentRepository).save(saved.capture());
    return saved.getValue();
  }

  @Test
  @DisplayName("An individual assignment is saved in the edition with its weight as a fraction and the name trimmed")
  void individualAssignmentIsSaved() {
    // Act
    AssignmentResponseDTO response = assignmentService.createAssignment(EDITION_ID, exam(30));

    // Assert
    Assignment saved = savedAssignment();
    assertSame(edition, saved.getSubjectEdition());
    assertEquals(new BigDecimal("0.30"), saved.getWeight());
    assertNull(saved.getGroupingRule());
    assertEquals("Final Exam", response.name());
    assertEquals(30, response.weight());
    assertNull(response.grouping());
  }

  @Test
  @DisplayName("A group project is saved with its sizes, same-class option and separations")
  void groupProjectIsSaved() {
    // Act
    AssignmentResponseDTO response = assignmentService.createAssignment(EDITION_ID,
        project(AssignmentType.PROJECT, 2, pair(1001L, 1002L)));

    // Assert
    assertEquals(1, savedAssignment().getGroupingRule().getSeparations().size());
    assertEquals(2, response.grouping().minSize());
    assertEquals(3, response.grouping().optimalSize());
    assertEquals(4, response.grouping().maxSize());
    assertTrue(response.grouping().sameClass());
    assertEquals(List.of(new AssignmentResponseDTO.Separation(1001L, 1002L)), response.grouping().separations());
  }

  @Test
  @DisplayName("A name the edition already has gets ASSIGNMENT_ALREADY_EXISTS, checked without the spaces around it")
  void repeatedNameIsRefused() {
    // Arrange - the request sends " Final Exam "
    when(assignmentRepository.existsBySubjectEditionAndNameIgnoreCase(edition, "Final Exam")).thenReturn(true);

    // Act
    StudyMatchException e = assertThrows(StudyMatchException.class,
        () -> assignmentService.createAssignment(EDITION_ID, exam(30)));

    // Assert
    assertEquals(ErrorCode.ASSIGNMENT_ALREADY_EXISTS, e.getCode());
    verify(assignmentRepository, never()).save(any());
  }

  // weights

  @Test
  @DisplayName("The weights can add up to exactly 100%: 70% used + 30% (boundary)")
  void weightsUpToExactlyFull() {
    // Arrange
    alreadyUsed("0.70");

    // Act
    assignmentService.createAssignment(EDITION_ID, exam(30));

    // Assert
    verify(assignmentRepository).save(any(Assignment.class));
  }

  @Test
  @DisplayName("70% used + 31% gets ASSIGNMENT_WEIGHT_EXCEEDED with the 30% left, and nothing is saved (boundary)")
  void weightsOverFullAreRefused() {
    // Arrange
    alreadyUsed("0.70");

    // Act
    StudyMatchException e = assertThrows(StudyMatchException.class,
        () -> assignmentService.createAssignment(EDITION_ID, exam(31)));

    // Assert
    assertEquals(ErrorCode.ASSIGNMENT_WEIGHT_EXCEEDED, e.getCode());
    assertEquals(Map.of("remaining", 30), e.getParams());
    verify(assignmentRepository, never()).save(any());
  }

  @Test
  @DisplayName("An edition already at 100% has 0% left, even for a 1% assignment")
  void fullEditionIsRefused() {
    // Arrange
    alreadyUsed("1.00");

    // Act
    StudyMatchException e = assertThrows(StudyMatchException.class,
        () -> assignmentService.createAssignment(EDITION_ID, exam(1)));

    // Assert
    assertEquals(Map.of("remaining", 0), e.getParams());
  }

  // grouping

  @Test
  @DisplayName("Group work on an exam gets GROUP_WORK_NOT_ALLOWED_FOR_EXAMS before any student is looked up")
  void examWithGroupingIsRefused() {
    // Act
    StudyMatchException e = assertThrows(StudyMatchException.class,
        () -> assignmentService.createAssignment(EDITION_ID, project(AssignmentType.PRACTICAL_EXAM, 2, pair(1001L, 1002L))));

    // Assert
    assertEquals(ErrorCode.GROUP_WORK_NOT_ALLOWED_FOR_EXAMS, e.getCode());
    verify(enrolmentService, never()).getEnrolledStudent(any(), any());
    verify(assignmentRepository, never()).save(any());
  }

  @Test
  @DisplayName("A minimum size of 1 gets INVALID_GROUP_SIZES (boundary, 2 is the smallest group)")
  void groupOfOneIsRefused() {
    // Act
    StudyMatchException e = assertThrows(StudyMatchException.class,
        () -> assignmentService.createAssignment(EDITION_ID, project(AssignmentType.PROJECT, 1)));

    // Assert
    assertEquals(ErrorCode.INVALID_GROUP_SIZES, e.getCode());
    verify(assignmentRepository, never()).save(any());
  }

  @Test
  @DisplayName("A student who isn't in the edition: STUDENT_NOT_ENROLLED goes through and nothing is saved")
  void studentNotEnrolledIsRefused() {
    // Arrange
    when(enrolmentService.getEnrolledStudent(EDITION_ID, 9999L))
        .thenThrow(new StudyMatchException(ErrorCode.STUDENT_NOT_ENROLLED, Map.of("number", 9999L)));

    // Act
    StudyMatchException e = assertThrows(StudyMatchException.class,
        () -> assignmentService.createAssignment(EDITION_ID, project(AssignmentType.PROJECT, 2, pair(1001L, 9999L))));

    // Assert
    assertEquals(ErrorCode.STUDENT_NOT_ENROLLED, e.getCode());
    verify(assignmentRepository, never()).save(any());
  }

  @Test
  @DisplayName("A student separated from themselves gets SEPARATION_SAME_STUDENT")
  void separationWithSameStudentIsRefused() {
    // Act
    StudyMatchException e = assertThrows(StudyMatchException.class,
        () -> assignmentService.createAssignment(EDITION_ID, project(AssignmentType.PROJECT, 2, pair(1001L, 1001L))));

    // Assert
    assertEquals(ErrorCode.SEPARATION_SAME_STUDENT, e.getCode());
  }

  @Test
  @DisplayName("The same pair the other way around gets SEPARATION_ALREADY_EXISTS")
  void repeatedSeparationIsRefused() {
    // Act
    StudyMatchException e = assertThrows(StudyMatchException.class, () -> assignmentService.createAssignment(EDITION_ID,
        project(AssignmentType.PROJECT, 2, pair(1001L, 1002L), pair(1002L, 1001L))));

    // Assert
    assertEquals(ErrorCode.SEPARATION_ALREADY_EXISTS, e.getCode());
  }

  @Test
  @DisplayName("An edition that doesn't exist: NOT_FOUND goes through and nothing is saved")
  void unknownEditionIsNotFound() {
    // Arrange
    when(subjectEditionService.getLockedEdition(99L)).thenThrow(new StudyMatchException(ErrorCode.NOT_FOUND));

    // Act
    StudyMatchException e = assertThrows(StudyMatchException.class,
        () -> assignmentService.createAssignment(99L, exam(30)));

    // Assert
    assertEquals(ErrorCode.NOT_FOUND, e.getCode());
    verify(assignmentRepository, never()).save(any());
  }

}
