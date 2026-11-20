package com.studymatch.models;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.studymatch.enums.AssignmentType;
import com.studymatch.exceptions.ErrorCode;
import com.studymatch.exceptions.StudyMatchException;

class GradeTest {

  private Enrolment enrolment;
  private Assignment assignment;

  @BeforeEach
  void setUp() {
    enrolment = ModelTestData.enrolment(ModelTestData.student("Test Student"));
    assignment = new Assignment(enrolment.getSubjectEdition(), "Test Exam", AssignmentType.EXAM,
        LocalDate.of(2030, 6, 1), new BigDecimal("0.50"), null);
  }

  // grades are 0 to 20: test just outside and at both ends

  @ParameterizedTest(name = "grade {0} is accepted")
  @ValueSource(doubles = { 0, 20 })
  @DisplayName("Grades of 0 and 20 are accepted")
  void gradesAtTheLimitsAreAccepted(double value) {
    // Act
    Grade grade = new Grade(enrolment, assignment, value);

    // Assert
    assertEquals(value, grade.getValue());
  }

  @ParameterizedTest(name = "grade {0} is refused")
  @ValueSource(doubles = { -0.1, 20.1 })
  @DisplayName("A new grade outside 0 to 20 gives GRADE_OUT_OF_RANGE")
  void newGradeOutOfRange(double value) {
    // Act
    StudyMatchException e = assertThrows(StudyMatchException.class, () -> new Grade(enrolment, assignment, value));

    // Assert
    assertEquals(ErrorCode.GRADE_OUT_OF_RANGE, e.getCode());
  }

  @ParameterizedTest(name = "changing to {0} is refused")
  @ValueSource(doubles = { -0.1, 20.1 })
  @DisplayName("Changing a grade to outside 0 to 20 gives GRADE_OUT_OF_RANGE and the old value stays")
  void changedGradeOutOfRange(double value) {
    // Arrange
    Grade grade = new Grade(enrolment, assignment, 14);

    // Act
    StudyMatchException e = assertThrows(StudyMatchException.class, () -> grade.setValue(value));

    // Assert
    assertEquals(ErrorCode.GRADE_OUT_OF_RANGE, e.getCode());
    assertEquals(14, grade.getValue());
  }

}
