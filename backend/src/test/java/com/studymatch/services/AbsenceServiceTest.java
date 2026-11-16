package com.studymatch.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.studymatch.models.Absence;
import com.studymatch.models.Enrolment;
import com.studymatch.models.Lesson;
import com.studymatch.models.ModelTestData;
import com.studymatch.repositories.AbsenceRepository;

class AbsenceServiceTest {

  private AbsenceRepository absenceRepository;
  private AbsenceService absenceService;

  @BeforeEach
  void setUp() {
    absenceRepository = mock(AbsenceRepository.class);
    absenceService = new AbsenceService(absenceRepository);
  }

  @Test
  @DisplayName("Counts only the unjustified absences of an enrolment, in its current class")
  void countsUnjustifiedAbsences() {
    // Arrange
    Enrolment enrolment = ModelTestData.enrolment(ModelTestData.student("Test Student"));
    when(absenceRepository.countByEnrolmentAndJustifiedFalseAndLesson_ClassSection(enrolment,
        enrolment.getClassSection())).thenReturn(3L);

    // Act
    long absences = absenceService.countUnjustifiedAbsences(enrolment);

    // Assert
    assertEquals(3, absences);
  }

  @Test
  @DisplayName("A student marked absent for the first time gets a new absence")
  void newAbsenceIsSaved() {
    // Arrange
    Enrolment enrolment = ModelTestData.enrolment(ModelTestData.student("Test Student"));
    Lesson lesson = lessonOf(enrolment);
    when(absenceRepository.findByLesson(lesson)).thenReturn(List.of());

    // Act
    absenceService.registerAbsences(lesson, List.of(enrolment), Map.of(enrolment, true));

    // Assert
    ArgumentCaptor<Absence> saved = ArgumentCaptor.forClass(Absence.class);
    verify(absenceRepository).save(saved.capture());
    assertSame(enrolment, saved.getValue().getEnrolment());
    assertSame(lesson, saved.getValue().getLesson());
    assertTrue(saved.getValue().isJustified());
  }

  @Test
  @DisplayName("Correcting an absence changes whether it's justified, without a second absence")
  void existingAbsenceIsCorrected() {
    // Arrange
    Enrolment enrolment = ModelTestData.enrolment(ModelTestData.student("Test Student"));
    Lesson lesson = lessonOf(enrolment);
    Absence absence = new Absence(lesson, enrolment, false);
    when(absenceRepository.findByLesson(lesson)).thenReturn(List.of(absence));

    // Act
    absenceService.registerAbsences(lesson, List.of(enrolment), Map.of(enrolment, true));

    // Assert
    assertTrue(absence.isJustified());
    verify(absenceRepository, never()).save(any());
  }

  @Test
  @DisplayName("A student no longer marked absent loses the absence")
  void presentStudentLosesAbsence() {
    // Arrange
    Enrolment enrolment = ModelTestData.enrolment(ModelTestData.student("Test Student"));
    Lesson lesson = lessonOf(enrolment);
    Absence absence = new Absence(lesson, enrolment, false);
    when(absenceRepository.findByLesson(lesson)).thenReturn(List.of(absence));

    // Act
    absenceService.registerAbsences(lesson, List.of(enrolment), Map.of());

    // Assert
    verify(absenceRepository).delete(absence);
  }

  @Test
  @DisplayName("An absence of a student who moved to another class is left alone")
  void movedStudentKeepsAbsence() {
    // Arrange
    Enrolment stayed = ModelTestData.enrolment(ModelTestData.student("Stayed Student"));
    Enrolment moved = ModelTestData.enrolment(ModelTestData.student("Moved Student"));
    Lesson lesson = lessonOf(stayed);
    Absence movedAbsence = new Absence(lesson, moved, false);
    when(absenceRepository.findByLesson(lesson)).thenReturn(List.of(movedAbsence));

    // Act
    absenceService.registerAbsences(lesson, List.of(stayed), Map.of());

    // Assert
    verify(absenceRepository, never()).delete(any());
    assertFalse(movedAbsence.isJustified());
  }

  private static Lesson lessonOf(Enrolment enrolment) {
    return new Lesson(enrolment.getClassSection(), LocalDateTime.of(2026, 11, 10, 9, 0));
  }

}
