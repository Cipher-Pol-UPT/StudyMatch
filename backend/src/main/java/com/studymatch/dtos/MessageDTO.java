package com.studymatch.dtos;

import java.time.LocalDateTime;

import com.studymatch.models.Message;

// a message as the chat shows it, editedAt is null until it's edited
public record MessageDTO(Long id, Long chatId, ChatContactDTO author, String text, LocalDateTime sentAt,
    LocalDateTime editedAt) {

  public static MessageDTO from(Message message) {
    return new MessageDTO(message.getId(), message.getChat().getId(), ChatContactDTO.from(message.getAuthor()),
        message.getText(), message.getSentAt(), message.getEditedAt());
  }

}
