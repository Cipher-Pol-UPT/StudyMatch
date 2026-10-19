package com.studymatch.dtos;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

// where the subject sits in one degree's study plan
public record DegreePlacementDTO(
    @NotNull Long degreeId,
    @NotNull @Min(1) @Max(6) Integer curricularYear,
    @NotNull @Min(1) @Max(2) Integer semester) {

}
