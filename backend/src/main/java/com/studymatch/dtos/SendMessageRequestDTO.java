package com.studymatch.dtos;

import java.util.List;

import com.studymatch.enums.Sticker;
import com.studymatch.models.CustomChat;
import com.studymatch.models.Message;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// a text of up to 2000 characters, a GIF or a sticker, exactly one of them (GR10). replyToId is null when it isn't a
// reply. a sticker that doesn't exist can't be read, so it's VALIDATION_FAILED naming its field (#214).
// the ones with attachments are SendAttachmentsRequestDTO
public record SendMessageRequestDTO(@Size(max = Message.MAX_TEXT_LENGTH) String text,
    @Pattern(regexp = "[A-Za-z0-9]{1," + Message.MAX_GIF_ID_LENGTH + "}") String gifId, Long replyToId,
    Sticker sticker, @Size(max = CustomChat.MAX_MEMBERS) List<Long> mentions) {

  // mentions are the numbers picked after @, saved with it, not read from the text (#239)
  public SendMessageRequestDTO {
    text = text == null ? "" : text.strip();
    mentions = mentions == null ? List.of() : mentions;
  }

  public SendMessageRequestDTO(String text, String gifId, Long replyToId) {
    this(text, gifId, replyToId, null);
  }

  public SendMessageRequestDTO(String text, String gifId, Long replyToId, Sticker sticker) {
    this(text, gifId, replyToId, sticker, null);
  }

  // in the service and not a constraint, it's about several fields
  public boolean hasOneContent() {
    int contents = (text.isEmpty() ? 0 : 1) + (gifId == null ? 0 : 1) + (sticker == null ? 0 : 1);
    return contents == 1;
  }

}
