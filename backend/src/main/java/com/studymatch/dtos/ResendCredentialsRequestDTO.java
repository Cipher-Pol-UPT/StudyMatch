package com.studymatch.dtos;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

// personalEmail is optional, only sent when staff is fixing a wrong one
public record ResendCredentialsRequestDTO(@Email @Size(max = 254) String personalEmail) {

  public ResendCredentialsRequestDTO {
    if (personalEmail != null) {
      personalEmail = personalEmail.isBlank() ? null : personalEmail.trim().toLowerCase();
    }
  }

}
