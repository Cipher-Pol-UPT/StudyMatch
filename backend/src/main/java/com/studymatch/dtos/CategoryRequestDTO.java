package com.studymatch.dtos;

import jakarta.validation.constraints.NotBlank;

public record CategoryRequestDTO(@NotBlank String name) {

}
