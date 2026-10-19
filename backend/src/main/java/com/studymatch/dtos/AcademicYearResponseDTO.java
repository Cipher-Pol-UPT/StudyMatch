package com.studymatch.dtos;

import com.studymatch.models.AcademicYear;

// startYear 2026 is the 2026/2027 academic year
public record AcademicYearResponseDTO(int startYear) {

  public static AcademicYearResponseDTO from(AcademicYear academicYear) {
    return new AcademicYearResponseDTO(academicYear.getStartYear());
  }

}
