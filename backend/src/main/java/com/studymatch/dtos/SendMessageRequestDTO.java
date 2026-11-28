package com.studymatch.dtos;

import com.studymatch.enums.Sticker;
import com.studymatch.models.Message;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// a text of up to 2000 characters, a GIF or a sticker, exactly one of them (GR10). replyToId is null when it isn't a
// reply. a sticker that doesn't exist can't be read, so it's VALIDATION_FAILED naming its field (#214).
// the ones with attachments are SendAttachmentsRequestDTO
public record SendMessageRequestDTO(@Size(max = Message.MAX_TEXT_LENGTH) String text,
    @Pattern(regexp = "[A-Za-z0-9]{1," + Message.MAX_GIF_ID_LENGTH + "}") String gifId, Long replyToId,
    Sticker sticker) {

  public SendMessageRequestDTO {
    text = text == null ? "" : text.strip();
  }

  public SendMessageRequestDTO(String text, String gifId, Long replyToId) {
    this(text, gifId, replyToId, null);
  }

  // in the service and not a constraint, it's about several fields
  public boolean hasOneContent() {
    int contents = (text.isEmpty() ? 0 : 1) + (gifId == null ? 0 : 1) + (sticker == null ? 0 : 1);
    return contents == 1;
  }

}
