package com.studymatch.dtos;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ForgotPasswordRequestDTO(@NotBlank @Email String email) {

  public ForgotPasswordRequestDTO {
    if (email != null) {
      email = email.trim();
    }
  }

}
