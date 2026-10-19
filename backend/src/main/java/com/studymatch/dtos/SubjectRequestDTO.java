package com.studymatch.dtos;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// a subject always belongs to at least one degree, and is registered together with its first edition
public record SubjectRequestDTO(
    @NotBlank @Size(max = 20) String code,
    @NotBlank @Size(max = 100) String name,
    @NotNull @Min(1) @Max(60) Integer ects,
    @NotNull Long categoryId,
    @NotEmpty List<@Valid @NotNull DegreePlacementDTO> degrees,
    @NotNull @Valid SubjectEditionRequestDTO edition) {

}
