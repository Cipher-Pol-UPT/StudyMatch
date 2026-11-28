package com.studymatch.models;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.studymatch.enums.RoomType;
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
    Degree otherDegree = new Degree("Other Test Degree", 3);

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
    Degree otherDegree = new Degree("Other Test Degree", 3);
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

  // weekly plan

  private static List<WeeklySession> sessions(int count) {
    return Collections.nCopies(count, new WeeklySession(120, RoomType.CLASSROOM));
  }

  @Test
  @DisplayName("A new subject has no weekly plan")
  void noPlanAtFirst() {
    assertTrue(subject.getWeeklySessions().isEmpty());
  }

  @Test
  @DisplayName("Setting the plan replaces the old one and keeps the order given")
  void setPlanReplaces() {
    // Arrange
    subject.setWeeklySessions(sessions(3));
    WeeklySession lab = new WeeklySession(180, RoomType.LAB);
    WeeklySession lecture = new WeeklySession(60, RoomType.AUDITORIUM);

    // Act
    subject.setWeeklySessions(List.of(lab, lecture));

    // Assert
    assertEquals(List.of(lab, lecture), subject.getWeeklySessions());
  }

  @Test
  @DisplayName("An empty plan clears it")
  void emptyPlanClears() {
    // Arrange
    subject.setWeeklySessions(sessions(2));

    // Act
    subject.setWeeklySessions(List.of());

    // Assert
    assertTrue(subject.getWeeklySessions().isEmpty());
  }

  @Test
  @DisplayName("At most 5 sessions a week (boundary: 5 ok, 6 is TOO_MANY_WEEKLY_SESSIONS)")
  void atMostFiveSessions() {
    // Act
    subject.setWeeklySessions(sessions(5));

    // Assert
    assertEquals(5, subject.getWeeklySessions().size());
    StudyMatchException e = assertThrows(StudyMatchException.class, () -> subject.setWeeklySessions(sessions(6)));
    assertEquals(ErrorCode.TOO_MANY_WEEKLY_SESSIONS, e.getCode());
    assertEquals(Map.of("max", 5), e.getParams());
  }

  @Test
  @DisplayName("A refused plan leaves the old one as it was")
  void refusedPlanKeepsOld() {
    // Arrange
    subject.setWeeklySessions(sessions(2));

    // Act
    assertThrows(StudyMatchException.class, () -> subject.setWeeklySessions(sessions(6)));

    // Assert
    assertEquals(2, subject.getWeeklySessions().size());
  }

  @Test
  @DisplayName("The plan can only change through setWeeklySessions")
  void planCannotBeModifiedDirectly() {
    assertThrows(UnsupportedOperationException.class, () -> subject.getWeeklySessions().clear());
  }

}
