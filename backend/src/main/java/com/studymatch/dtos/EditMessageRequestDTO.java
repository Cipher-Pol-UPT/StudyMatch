package com.studymatch.dtos;

import com.studymatch.models.Message;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// same text rule as sending, what it replies to never changes (GR10)
public record EditMessageRequestDTO(@NotBlank @Size(max = Message.MAX_TEXT_LENGTH) String text) {

  public EditMessageRequestDTO {
    if (text != null) {
      text = text.strip();
    }
  }

}
