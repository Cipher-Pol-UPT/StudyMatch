package com.studymatch.services;

import org.springframework.stereotype.Service;

import com.studymatch.models.Enrolment;
import com.studymatch.repositories.AbsenceRepository;

@Service
public class AbsenceService {

  private final AbsenceRepository absenceRepository;

  public AbsenceService(AbsenceRepository absenceRepository) {
    this.absenceRepository = absenceRepository;
  }

  // only the current class, lessons are counted from it too, so after a class change attendance can't go below 0
  public long countUnjustifiedAbsences(Enrolment enrolment) {
    return absenceRepository.countByEnrolmentAndJustifiedFalseAndLesson_ClassSection(enrolment,
        enrolment.getClassSection());
  }

}
