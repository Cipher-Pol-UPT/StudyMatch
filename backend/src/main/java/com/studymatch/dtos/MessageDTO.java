package com.studymatch.dtos;

import java.time.LocalDateTime;

import com.studymatch.models.Message;
import com.studymatch.models.User;

// a message as the viewer sees it, editedAt is null until it's edited.
// a reply with no replyTo is one whose original was deleted, or is hidden from the viewer (GR9)
public record MessageDTO(Long id, Long chatId, ChatContactDTO author, String text, LocalDateTime sentAt,
    LocalDateTime editedAt, boolean reply, MessageQuoteDTO replyTo) {

  public static MessageDTO from(Message message, User viewer) {
    Message original = message.getReplyTo();
    MessageQuoteDTO replyTo = original == null || original.isHiddenFrom(viewer) ? null : MessageQuoteDTO.from(original);
    return new MessageDTO(message.getId(), message.getChat().getId(), ChatContactDTO.from(message.getAuthor()),
        message.getText(), message.getSentAt(), message.getEditedAt(), message.isReply(), replyTo);
  }

}
