package com.studymatch.dtos;

import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PersonalityTestRequestDTO(
    @NotNull @Size(min = 15, max = 15) List<@NotNull @Min(1) @Max(5) Integer> answers) {

}
