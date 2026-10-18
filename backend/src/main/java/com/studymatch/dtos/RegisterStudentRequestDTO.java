package com.studymatch.dtos;

import com.studymatch.models.Degree;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// same DTO for the form and for each CSV row, so both follow the same rules
public record RegisterStudentRequestDTO(
    @NotBlank @Size(max = 100) String name,
    @NotBlank @Email @Size(max = 254) String personalEmail,
    @NotBlank String degreeProgramme,
    @NotNull @Min(1) @Max(Degree.MAX_YEARS) Integer curricularYear) {

  public RegisterStudentRequestDTO {
    if (name != null) {
      name = name.trim();
    }
    if (personalEmail != null) {
      personalEmail = personalEmail.trim().toLowerCase();
    }
    if (degreeProgramme != null) {
      degreeProgramme = degreeProgramme.trim();
    }
  }

}
