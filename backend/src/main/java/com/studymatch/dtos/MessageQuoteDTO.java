package com.studymatch.dtos;

import java.util.List;

import com.studymatch.models.Message;

// the original a reply quotes, without its own quote if it's a reply too (#218)
public record MessageQuoteDTO(Long id, ChatContactDTO author, String text, List<AttachmentDTO> attachments) {

  public static MessageQuoteDTO from(Message message) {
    return new MessageQuoteDTO(message.getId(), ChatContactDTO.from(message.getAuthor()), message.getText(),
        message.getAttachments().stream().map(AttachmentDTO::from).toList());
  }

}
