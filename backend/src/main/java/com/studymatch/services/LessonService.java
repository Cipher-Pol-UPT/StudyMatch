package com.studymatch.services;

import org.springframework.stereotype.Service;

import com.studymatch.models.ClassSection;
import com.studymatch.repositories.LessonRepository;

@Service
public class LessonService {

  private final LessonRepository lessonRepository;

  public LessonService(LessonRepository lessonRepository) {
    this.lessonRepository = lessonRepository;
  }

  // every stored lesson counts for attendance, so only lessons that already happened should be stored
  public long countLessons(ClassSection classSection) {
    return lessonRepository.countByClassSection(classSection);
  }

}
