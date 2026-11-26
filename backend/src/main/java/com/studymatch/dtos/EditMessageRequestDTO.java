package com.studymatch.dtos;

import com.studymatch.models.Message;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// same text rule as sending, empty only if it has attachments. what it replies to never changes (GR10)
public record EditMessageRequestDTO(@NotNull @Size(max = Message.MAX_TEXT_LENGTH) String text) {

  public EditMessageRequestDTO {
    if (text != null) {
      text = text.strip();
    }
  }

}
