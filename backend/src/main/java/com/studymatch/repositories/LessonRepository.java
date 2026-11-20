package com.studymatch.repositories;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.studymatch.models.ClassSection;
import com.studymatch.models.Lesson;

public interface LessonRepository extends JpaRepository<Lesson, Long> {

  long countByClassSection(ClassSection classSection);

  // [class section id, lessons], classes with no lessons yet aren't in the result
  @Query("""
      select l.classSection.id, count(l) from Lesson l
      where l.classSection in :classSections
      group by l.classSection.id""")
  List<Object[]> countByClassSectionIn(Collection<ClassSection> classSections);

  Optional<Lesson> findByClassSectionAndStartsAt(ClassSection classSection, LocalDateTime startsAt);

}
