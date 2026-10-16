package com.studymatch.dtos;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequestDTO(@NotBlank @Email String email, @NotBlank String password) {

  public LoginRequestDTO {
    if (email != null) {
      email = email.trim();
    }
  }

}
