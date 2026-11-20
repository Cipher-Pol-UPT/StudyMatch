package com.studymatch.dtos;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ApplyTutorRequestDTO(@NotNull Long subjectId, @Size(max = 500) String motivation) {
}
