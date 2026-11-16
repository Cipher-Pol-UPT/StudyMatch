package com.studymatch.repositories;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.studymatch.models.ClassSection;
import com.studymatch.models.Lesson;

public interface LessonRepository extends JpaRepository<Lesson, Long> {

  long countByClassSection(ClassSection classSection);

  Optional<Lesson> findByClassSectionAndStartsAt(ClassSection classSection, LocalDateTime startsAt);

}
