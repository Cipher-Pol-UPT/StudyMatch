package com.studymatch.models;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.studymatch.enums.EnrolmentStatus;
import com.studymatch.exceptions.ErrorCode;
import com.studymatch.exceptions.StudyMatchException;

class EnrolmentTest {

  private SubjectEdition edition;
  private Enrolment enrolment;

  @BeforeEach
  void setUp() {
    edition = ModelTestData.subjectEdition();
    edition.addClassSection("T1");
    enrolment = new Enrolment(ModelTestData.student("Test Student"), classSection(edition, "T1"));
  }

  private ClassSection classSection(SubjectEdition subjectEdition, String code) {
    return subjectEdition.getClassSections().stream()
        .filter(cs -> cs.getCode().equals(code))
        .findFirst()
        .orElseThrow();
  }

  @Test
  @DisplayName("A new enrolment is in progress, with no grade, in its class's edition")
  void newEnrolmentStartsInProgress() {
    assertEquals(EnrolmentStatus.IN_PROGRESS, enrolment.getStatus());
    assertNull(enrolment.getFinalGrade());
    assertSame(edition, enrolment.getSubjectEdition());
  }

  // pass mark is 10: test just below, at and above it

  @ParameterizedTest(name = "grade {0} passes")
  @ValueSource(ints = { 10, 11, 20 })
  @DisplayName("Final grades of 10 or more pass")
  void passingGrades(int grade) {
    // Act
    enrolment.setFinalGrade(grade);

    // Assert
    assertEquals(EnrolmentStatus.PASSED, enrolment.getStatus());
    assertEquals(grade, enrolment.getFinalGrade());
  }

  @ParameterizedTest(name = "grade {0} fails")
  @ValueSource(ints = { 0, 9 })
  @DisplayName("Final grades below 10 fail")
  void failingGrades(int grade) {
    // Act
    enrolment.setFinalGrade(grade);

    // Assert
    assertEquals(EnrolmentStatus.FAILED, enrolment.getStatus());
  }

  @Test
  @DisplayName("Re-grading updates the status")
  void regradingUpdatesStatus() {
    // Arrange
    enrolment.setFinalGrade(9);

    // Act - teacher corrects the grade
    enrolment.setFinalGrade(12);

    // Assert
    assertEquals(EnrolmentStatus.PASSED, enrolment.getStatus());
  }

  // changing class

  @Test
  @DisplayName("A student can move to another class of the same edition")
  void changeToClassOfSameEdition() {
    // Arrange
    edition.addClassSection("T2");
    ClassSection t2 = classSection(edition, "T2");

    // Act
    enrolment.changeClassSection(t2);

    // Assert
    assertSame(t2, enrolment.getClassSection());
  }

  @Test
  @DisplayName("A student can't move to a class of another edition")
  void changeToClassOfOtherEditionIsRejected() {
    // Arrange
    SubjectEdition otherEdition = ModelTestData.subjectEdition();
    otherEdition.addClassSection("T1");

    // Act
    StudyMatchException e = assertThrows(StudyMatchException.class,
        () -> enrolment.changeClassSection(classSection(otherEdition, "T1")));

    // Assert
    assertEquals(ErrorCode.CLASS_SECTION_NOT_IN_SUBJECT_EDITION, e.getCode());
  }

}
