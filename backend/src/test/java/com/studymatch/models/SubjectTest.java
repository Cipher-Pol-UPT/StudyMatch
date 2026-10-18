package com.studymatch.models;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.studymatch.exceptions.ErrorCode;
import com.studymatch.exceptions.StudyMatchException;

class SubjectTest {

  private Subject subject;
  private Degree degree;

  @BeforeEach
  void setUp() {
    subject = ModelTestData.subject();
    degree = ModelTestData.degree();
  }

  @Test
  @DisplayName("Adding a degree links it with its curricular year and semester")
  void addDegreeCreatesLink() {
    // Act
    subject.addDegree(degree, 2, 1);

    // Assert
    assertEquals(1, subject.getDegreeSubjects().size());
    DegreeSubject link = subject.getDegreeSubjects().iterator().next();
    assertSame(degree, link.getDegree());
    assertEquals(2, link.getCurricularYear());
    assertEquals(1, link.getSemester());
  }

  @Test
  @DisplayName("The same subject can be in two degrees with different years")
  void subjectCanBelongToSeveralDegrees() {
    // Arrange
    Degree otherDegree = new Degree("Other Test Degree");

    // Act
    subject.addDegree(degree, 1, 1);
    subject.addDegree(otherDegree, 2, 2);

    // Assert
    assertEquals(2, subject.getDegreeSubjects().size());
  }

  @Test
  @DisplayName("A subject can't be added to the same degree twice")
  void sameDegreeTwiceIsRejected() {
    // Arrange
    subject.addDegree(degree, 1, 1);

    // Act - even with a different year/semester it's still the same degree
    StudyMatchException e = assertThrows(StudyMatchException.class, () -> subject.addDegree(degree, 2, 2));

    // Assert
    assertEquals(ErrorCode.SUBJECT_ALREADY_IN_DEGREE, e.getCode());
  }

  @Test
  @DisplayName("Removing a degree only removes that degree")
  void removeDegreeKeepsTheOthers() {
    // Arrange
    Degree otherDegree = new Degree("Other Test Degree");
    subject.addDegree(degree, 1, 1);
    subject.addDegree(otherDegree, 1, 1);

    // Act
    subject.removeDegree(degree);

    // Assert
    assertEquals(1, subject.getDegreeSubjects().size());
    assertSame(otherDegree, subject.getDegreeSubjects().iterator().next().getDegree());
  }

  @Test
  @DisplayName("Degree links can only change through addDegree/removeDegree")
  void degreeSubjectsCannotBeModifiedDirectly() {
    // Arrange
    subject.addDegree(degree, 1, 1);

    // Act + Assert - read-only view
    assertThrows(UnsupportedOperationException.class, () -> subject.getDegreeSubjects().clear());
  }

}
