package com.studymatch.services;

import org.springframework.stereotype.Service;

import com.studymatch.repositories.AcademicYearRepository;

@Service
public class AcademicYearService {

  private final AcademicYearRepository academicYearRepository;

  public AcademicYearService(AcademicYearRepository academicYearRepository) {
    this.academicYearRepository = academicYearRepository;
  }

}
