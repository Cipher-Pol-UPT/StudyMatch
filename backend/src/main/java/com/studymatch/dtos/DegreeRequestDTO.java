package com.studymatch.dtos;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record DegreeRequestDTO(
    @NotBlank @Size(max = 100) String name,
    @NotNull @Min(1) @Max(6) Integer curricularYears) {

}
