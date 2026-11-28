package com.studymatch.dtos;

import com.studymatch.models.Message;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// a text of up to 2000 characters or a GIF, never both or neither (GR10). replyToId is null when it isn't a reply.
// the ones with attachments are SendAttachmentsRequestDTO
public record SendMessageRequestDTO(@Size(max = Message.MAX_TEXT_LENGTH) String text,
    @Pattern(regexp = "[A-Za-z0-9]{1," + Message.MAX_GIF_ID_LENGTH + "}") String gifId, Long replyToId) {

  public SendMessageRequestDTO {
    text = text == null ? "" : text.strip();
  }

  // in the service and not a constraint, it's about two fields
  public boolean hasOneContent() {
    return text.isEmpty() != (gifId == null);
  }

}
