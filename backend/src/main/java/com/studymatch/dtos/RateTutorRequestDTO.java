package com.studymatch.dtos;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// the comment is optional and public, it shows on the tutor's profile without the student's name
public record RateTutorRequestDTO(
    @NotNull @Min(1) @Max(5) Integer stars,
    @Size(max = 500) String comment) {
}
