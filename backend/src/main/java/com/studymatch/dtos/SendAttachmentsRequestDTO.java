package com.studymatch.dtos;

import java.util.List;

import org.springframework.web.multipart.MultipartFile;

import com.studymatch.models.CustomChat;
import com.studymatch.models.Message;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

// a message with files, sent as a form. the text is optional and has the same limit as without them (GR10)
public record SendAttachmentsRequestDTO(@NotEmpty List<MultipartFile> files,
    @Size(max = Message.MAX_TEXT_LENGTH) String text, Long replyToId,
    @Size(max = CustomChat.MAX_MEMBERS) List<Long> mentions) {

  // mentions as one form field each, the same as without files (#239)
  public SendAttachmentsRequestDTO {
    text = text == null ? "" : text.strip();
    mentions = mentions == null ? List.of() : mentions;
  }

}
