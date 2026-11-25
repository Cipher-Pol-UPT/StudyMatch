package com.studymatch.dtos;

import com.studymatch.models.Message;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// without attachments (SendAttachmentsRequestDTO), so never empty and up to 2000 characters (GR10). replyToId is null when it isn't a reply
public record SendMessageRequestDTO(@NotBlank @Size(max = Message.MAX_TEXT_LENGTH) String text,
    Long replyToId) {

  public SendMessageRequestDTO {
    if (text != null) {
      text = text.strip();
    }
  }

}
