package com.studymatch.dtos;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// same rules as registering, but the degree comes from a dropdown so it's the id instead of the name
public record EditStudentRequestDTO(
    @NotBlank @Size(max = 100) String name,
    @NotBlank @Email @Size(max = 254) String personalEmail,
    @NotNull Long degreeId,
    @NotNull @Min(1) @Max(6) Integer curricularYear) {

  public EditStudentRequestDTO {
    if (name != null) {
      name = name.trim();
    }
    if (personalEmail != null) {
      personalEmail = personalEmail.trim().toLowerCase();
    }
  }

}
