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

  public long countLessons(ClassSection classSection) {
    return lessonRepository.countByClassSection(classSection);
  }

}
