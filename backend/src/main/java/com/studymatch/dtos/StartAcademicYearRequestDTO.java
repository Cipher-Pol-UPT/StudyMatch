package com.studymatch.dtos;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

// the year Staff saw in the preview, so a double click can't start two years, and its semester dates
public record StartAcademicYearRequestDTO(@NotNull Integer startYear, @NotNull @Valid SemesterDatesDTO semesters) {
}
