package com.studymatch.dtos;

import com.studymatch.enums.Role;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// username is only the part before the @, the domain is added by the System
public record CreateAccountRequestDTO(
    @NotNull Role type,
    @NotBlank @Size(max = 100) String name,
    @NotBlank @Email @Size(max = 254) String personalEmail,
    @NotBlank @Size(max = 64) @Pattern(regexp = "[a-z0-9]+(\\.[a-z0-9]+)*") String username) {

  public CreateAccountRequestDTO {
    if (name != null) {
      name = name.trim();
    }
    if (personalEmail != null) {
      personalEmail = personalEmail.trim().toLowerCase();
    }
    if (username != null) {
      username = username.trim().toLowerCase();
    }
  }

}
