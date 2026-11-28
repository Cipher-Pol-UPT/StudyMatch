package com.studymatch.dtos;

import java.util.List;

import org.springframework.web.multipart.MultipartFile;

import com.studymatch.models.Message;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

// a message with files, sent as a form. the text is optional and has the same limit as without them (GR10)
public record SendAttachmentsRequestDTO(@NotEmpty List<MultipartFile> files,
    @Size(max = Message.MAX_TEXT_LENGTH) String text, Long replyToId) {

  public SendAttachmentsRequestDTO {
    text = text == null ? "" : text.strip();
  }

}
