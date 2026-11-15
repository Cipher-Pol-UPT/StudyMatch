package com.studymatch.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BlockAccountRequestDTO(@NotBlank @Size(max = 500) String reason) {

  public BlockAccountRequestDTO {
    if (reason != null) {
      reason = reason.trim();
    }
  }

}
