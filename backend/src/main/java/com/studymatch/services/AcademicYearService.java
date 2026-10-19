package com.studymatch.services;

import java.util.List;

import org.springframework.stereotype.Service;

import com.studymatch.models.AcademicYear;
import com.studymatch.repositories.AcademicYearRepository;

@Service
public class AcademicYearService {

  private final AcademicYearRepository academicYearRepository;

  public AcademicYearService(AcademicYearRepository academicYearRepository) {
    this.academicYearRepository = academicYearRepository;
  }

  // newest first, the year Staff usually wants is at the top
  public List<AcademicYear> listAcademicYears() {
    return academicYearRepository.findAllByOrderByStartYearDesc();
  }

}
