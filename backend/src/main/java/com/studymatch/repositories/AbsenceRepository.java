package com.studymatch.repositories;

import org.springframework.data.jpa.repository.JpaRepository;

import com.studymatch.models.Absence;
import com.studymatch.models.ClassSection;
import com.studymatch.models.Enrolment;

public interface AbsenceRepository extends JpaRepository<Absence, Long> {

  long countByEnrolmentAndJustifiedFalseAndLesson_ClassSection(Enrolment enrolment, ClassSection classSection);

}
