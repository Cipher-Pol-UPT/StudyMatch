package com.studymatch.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.studymatch.models.Absence;
import com.studymatch.models.ClassSection;
import com.studymatch.models.Enrolment;
import com.studymatch.models.Lesson;

public interface AbsenceRepository extends JpaRepository<Absence, Long> {

  long countByEnrolmentAndJustifiedFalseAndLesson_ClassSection(Enrolment enrolment, ClassSection classSection);

  List<Absence> findByLesson(Lesson lesson);

  void deleteByLesson(Lesson lesson);

}
