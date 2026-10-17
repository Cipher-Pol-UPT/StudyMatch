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

  public long countUnjustifiedAbsences(Enrolment enrolment) {
    return absenceRepository.countByEnrolmentAndJustifiedFalse(enrolment);
  }

}
