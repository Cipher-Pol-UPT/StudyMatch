package com.studymatch.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// the class of the same edition the student moves to, same limit as when enrolling
public record ChangeClassRequestDTO(@NotBlank @Size(max = 20) String classSection) {
}
