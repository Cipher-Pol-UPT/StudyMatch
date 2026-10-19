package com.studymatch.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DegreeRequestDTO(@NotBlank @Size(max = 100) String name) {

}
