package com.studymatch.services;

import java.util.List;
import java.util.Optional;

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

  // the newest registered year is the current one, starting a new year just adds a newer one
  public Optional<AcademicYear> findCurrent() {
    return academicYearRepository.findFirstByOrderByStartYearDesc();
  }

  public boolean isCurrent(AcademicYear year) {
    return findCurrent().map(current -> current.getStartYear() == year.getStartYear()).orElse(false);
  }

}
