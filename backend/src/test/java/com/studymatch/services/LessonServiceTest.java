package com.studymatch.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.studymatch.exceptions.ErrorCode;
import com.studymatch.exceptions.StudyMatchException;
import com.studymatch.models.ClassSection;
import com.studymatch.models.Lesson;
import com.studymatch.models.ModelTestData;
import com.studymatch.repositories.LessonRepository;

class LessonServiceTest {

  private LessonRepository lessonRepository;
  private LessonService lessonService;

  @BeforeEach
  void setUp() {
    lessonRepository = mock(LessonRepository.class);
    lessonService = new LessonService(lessonRepository);
  }

  @Test
  @DisplayName("Counts the lessons of a class section")
  void countsLessonsOfAClassSection() {
    // Arrange
    ClassSection classSection = ModelTestData.enrolment(ModelTestData.student("Test Student")).getClassSection();
    when(lessonRepository.countByClassSection(classSection)).thenReturn(12L);

    // Act
    long lessons = lessonService.countLessons(classSection);

    // Assert
    assertEquals(12, lessons);
  }

  @Test
  @DisplayName("The seconds of a lesson start are dropped, so 9:00:30 is the 9:00 lesson")
  void lessonStartDropsSeconds() {
    // Arrange
    LocalDateTime startsAt = LocalDateTime.of(2026, 11, 10, 9, 0, 30, 500);

    // Act
    LocalDateTime start = lessonService.lessonStart(startsAt);

    // Assert
    assertEquals(LocalDateTime.of(2026, 11, 10, 9, 0), start);
  }

  @Test
  @DisplayName("A lesson that started this minute can be registered")
  void lessonStartingThisMinuteIsAccepted() {
    // Arrange
    LocalDateTime thisMinute = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES);

    // Act
    LocalDateTime start = lessonService.lessonStart(thisMinute);

    // Assert
    assertEquals(thisMinute, start);
  }

  @Test
  @DisplayName("A lesson starting next minute is refused with LESSON_IN_FUTURE")
  void lessonStartingNextMinuteIsRefused() {
    // Arrange
    LocalDateTime nextMinute = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES).plusMinutes(1);

    // Act
    StudyMatchException e = assertThrows(StudyMatchException.class, () -> lessonService.lessonStart(nextMinute));

    // Assert
    assertEquals(ErrorCode.LESSON_IN_FUTURE, e.getCode());
  }

  @Test
  @DisplayName("A lesson registered before is reused, nothing new is saved")
  void existingLessonIsReused() {
    // Arrange
    ClassSection classSection = classSection();
    LocalDateTime startsAt = LocalDateTime.of(2026, 11, 10, 9, 0);
    Lesson lesson = new Lesson(classSection, startsAt);
    when(lessonRepository.findByClassSectionAndStartsAt(classSection, startsAt)).thenReturn(Optional.of(lesson));

    // Act
    Lesson result = lessonService.getOrCreateLesson(classSection, startsAt);

    // Assert
    assertSame(lesson, result);
    verify(lessonRepository, never()).save(any());
  }

  @Test
  @DisplayName("A lesson nobody registered yet is created for that class and start")
  void newLessonIsCreated() {
    // Arrange
    ClassSection classSection = classSection();
    LocalDateTime startsAt = LocalDateTime.of(2026, 11, 10, 9, 0);
    when(lessonRepository.findByClassSectionAndStartsAt(classSection, startsAt)).thenReturn(Optional.empty());
    when(lessonRepository.save(any(Lesson.class))).thenAnswer(invocation -> invocation.getArgument(0));

    // Act
    lessonService.getOrCreateLesson(classSection, startsAt);

    // Assert
    ArgumentCaptor<Lesson> saved = ArgumentCaptor.forClass(Lesson.class);
    verify(lessonRepository).save(saved.capture());
    assertSame(classSection, saved.getValue().getClassSection());
    assertEquals(startsAt, saved.getValue().getStartsAt());
  }

  @Test
  @DisplayName("A registered lesson is found by its class and start")
  void registeredLessonIsFound() {
    // Arrange
    ClassSection classSection = classSection();
    LocalDateTime startsAt = LocalDateTime.of(2026, 11, 10, 9, 0);
    Lesson lesson = new Lesson(classSection, startsAt);
    when(lessonRepository.findByClassSectionAndStartsAt(classSection, startsAt)).thenReturn(Optional.of(lesson));

    // Act
    Lesson result = lessonService.getLesson(classSection, startsAt);

    // Assert
    assertSame(lesson, result);
  }

  @Test
  @DisplayName("A lesson nobody registered is NOT_FOUND")
  void unregisteredLessonIsNotFound() {
    // Arrange
    ClassSection classSection = classSection();
    LocalDateTime startsAt = LocalDateTime.of(2026, 11, 10, 9, 0);
    when(lessonRepository.findByClassSectionAndStartsAt(classSection, startsAt)).thenReturn(Optional.empty());

    // Act
    StudyMatchException e = assertThrows(StudyMatchException.class,
        () -> lessonService.getLesson(classSection, startsAt));

    // Assert
    assertEquals(ErrorCode.NOT_FOUND, e.getCode());
  }

  @Test
  @DisplayName("Deleting a lesson removes it from the repository")
  void deleteLessonRemovesIt() {
    // Arrange
    Lesson lesson = new Lesson(classSection(), LocalDateTime.of(2026, 11, 10, 9, 0));

    // Act
    lessonService.deleteLesson(lesson);

    // Assert
    verify(lessonRepository).delete(lesson);
  }

  private static ClassSection classSection() {
    return ModelTestData.enrolment(ModelTestData.student("Test Student")).getClassSection();
  }

}
