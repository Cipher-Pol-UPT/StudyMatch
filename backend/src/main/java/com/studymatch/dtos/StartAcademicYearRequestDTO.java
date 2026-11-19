package com.studymatch.dtos;

import jakarta.validation.constraints.NotNull;

// the year Staff saw in the preview, so a double click can't start two years
public record StartAcademicYearRequestDTO(@NotNull Integer startYear) {
}
