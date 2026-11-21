package com.studymatch.dtos;

import com.studymatch.models.Message;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// text only for now, never empty and up to 2000 characters (GR10)
public record SendMessageRequestDTO(@NotBlank @Size(max = Message.MAX_TEXT_LENGTH) String text) {

  public SendMessageRequestDTO {
    if (text != null) {
      text = text.strip();
    }
  }

}
