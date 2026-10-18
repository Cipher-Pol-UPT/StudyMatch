package com.studymatch.repositories;

import org.springframework.data.jpa.repository.JpaRepository;

import com.studymatch.models.ClassSection;
import com.studymatch.models.Lesson;

public interface LessonRepository extends JpaRepository<Lesson, Long> {

  long countByClassSection(ClassSection classSection);

}
