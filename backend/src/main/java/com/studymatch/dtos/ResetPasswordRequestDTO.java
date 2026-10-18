package com.studymatch.dtos;

import com.studymatch.security.PasswordPolicy;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequestDTO(
    @NotBlank @Email String email,
    @NotBlank String code,
    @NotBlank
    @Size(min = PasswordPolicy.MIN_LENGTH, max = PasswordPolicy.MAX_LENGTH)
    @Pattern(regexp = PasswordPolicy.PATTERN)
    String newPassword) {

  public ResetPasswordRequestDTO {
    if (email != null) {
      email = email.trim();
    }
    if (code != null) {
      code = code.trim();
    }
  }

}
