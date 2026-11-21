package com.studymatch.services;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.studymatch.exceptions.ErrorCode;
import com.studymatch.exceptions.StudyMatchException;
import com.studymatch.models.ClassSection;
import com.studymatch.models.Lesson;
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

  // analytics: every lesson of these classes in one query
  public List<Lesson> listLessons(Collection<ClassSection> classSections) {
    return lessonRepository.findByClassSectionIn(classSections);
  }

  // seconds dropped so 9:00 and 9:00:30 are the same lesson, and a future one would count as held
  public LocalDateTime lessonStart(LocalDateTime startsAt) {
    LocalDateTime start = startsAt.truncatedTo(ChronoUnit.MINUTES);
    if (start.isAfter(LocalDateTime.now())) {
      throw new StudyMatchException(ErrorCode.LESSON_IN_FUTURE);
    }
    return start;
  }

  // empty when nobody registered this lesson yet
  public Optional<Lesson> findLesson(ClassSection classSection, LocalDateTime startsAt) {
    return lessonRepository.findByClassSectionAndStartsAt(classSection, startsAt);
  }

  // the first record creates the lesson, a correction reuses it
  public Lesson getOrCreateLesson(ClassSection classSection, LocalDateTime startsAt) {
    return findLesson(classSection, startsAt)
        .orElseGet(() -> lessonRepository.save(new Lesson(classSection, startsAt)));
  }

  public Lesson getLesson(ClassSection classSection, LocalDateTime startsAt) {
    return findLesson(classSection, startsAt)
        .orElseThrow(() -> new StudyMatchException(ErrorCode.NOT_FOUND));
  }

  // its absences have to go first
  public void deleteLesson(Lesson lesson) {
    lessonRepository.delete(lesson);
  }

}
