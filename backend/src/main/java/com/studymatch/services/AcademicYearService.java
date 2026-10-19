package com.studymatch.services;

import java.util.List;

import org.springframework.stereotype.Service;

import com.studymatch.exceptions.ErrorCode;
import com.studymatch.exceptions.StudyMatchException;
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

  // only years already registered can be used, they aren't created on the fly
  public AcademicYear getAcademicYear(int startYear) {
    return academicYearRepository.findById(startYear)
        .orElseThrow(() -> new StudyMatchException(ErrorCode.NOT_FOUND));
  }

}
