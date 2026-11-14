package com.studymatch.dtos;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// the class the students go into and who (by student number), same limit as the CSV import
public record EnrolStudentsRequestDTO(
    @NotBlank @Size(max = 20) String classSection,
    @NotEmpty @Size(max = MAX_STUDENTS) List<@NotNull Long> studentNumbers) {

  public static final int MAX_STUDENTS = 200;

}
