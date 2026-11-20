package com.studymatch.repositories;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.studymatch.models.Absence;
import com.studymatch.models.ClassSection;
import com.studymatch.models.Enrolment;
import com.studymatch.models.Lesson;

public interface AbsenceRepository extends JpaRepository<Absence, Long> {

  long countByEnrolmentAndJustifiedFalseAndLesson_ClassSection(Enrolment enrolment, ClassSection classSection);

  // [enrolment id, unjustified absences] in the enrolment's current class, the same rule as the count above
  @Query("""
      select a.enrolment.id, count(a) from Absence a
      where a.enrolment in :enrolments
        and a.justified = false
        and a.lesson.classSection = a.enrolment.classSection
      group by a.enrolment.id""")
  List<Object[]> countUnjustifiedByEnrolmentIn(Collection<Enrolment> enrolments);

  List<Absence> findByLesson(Lesson lesson);

  void deleteByLesson(Lesson lesson);

}
