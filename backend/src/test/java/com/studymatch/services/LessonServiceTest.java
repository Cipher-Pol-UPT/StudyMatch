package com.studymatch.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.studymatch.models.ClassSection;
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

}
