package com.studymatch.dtos;

import java.time.LocalDate;

import com.studymatch.models.AcademicYear;

import jakarta.validation.constraints.NotNull;

// in when starting a year or editing its dates, out with the year. how the dates fit together is checked in AcademicYear
public record SemesterDatesDTO(
    @NotNull LocalDate semester1Start,
    @NotNull LocalDate semester1End,
    @NotNull LocalDate semester2Start,
    @NotNull LocalDate semester2End) {

  // null for a year from before #334, which has no dates
  public static SemesterDatesDTO from(AcademicYear academicYear) {
    if (!academicYear.hasSemesters()) {
      return null;
    }
    return new SemesterDatesDTO(academicYear.getSemester1Start(), academicYear.getSemester1End(),
        academicYear.getSemester2Start(), academicYear.getSemester2End());
  }

}
