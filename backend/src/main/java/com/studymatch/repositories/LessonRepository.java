package com.studymatch.repositories;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.studymatch.models.ClassSection;
import com.studymatch.models.Lesson;

public interface LessonRepository extends JpaRepository<Lesson, Long> {

  long countByClassSection(ClassSection classSection);

  List<Lesson> findByClassSectionIn(Collection<ClassSection> classSections);

  Optional<Lesson> findByClassSectionAndStartsAt(ClassSection classSection, LocalDateTime startsAt);

}
