package com.studymatch.dtos;

import java.time.LocalDateTime;
import java.util.List;

import com.studymatch.models.Message;
import com.studymatch.models.User;

// a message as the viewer sees it, editedAt is null until it's edited. text is empty if it only has attachments or
// is a GIF, and gifId is null if it isn't one.
// a reply with no replyTo is one whose original was deleted, or is hidden from the viewer (GR9)
public record MessageDTO(Long id, Long chatId, ChatContactDTO author, String text, String gifId, LocalDateTime sentAt,
    LocalDateTime editedAt, boolean reply, MessageQuoteDTO replyTo, List<AttachmentDTO> attachments) {

  public static MessageDTO from(Message message, User viewer) {
    Message original = message.getReplyTo();
    MessageQuoteDTO replyTo = original == null || original.isHiddenFrom(viewer) ? null : MessageQuoteDTO.from(original);
    return new MessageDTO(message.getId(), message.getChat().getId(), ChatContactDTO.from(message.getAuthor()),
        message.getText(), message.getGifId(), message.getSentAt(), message.getEditedAt(), message.isReply(), replyTo,
        message.getAttachments().stream().map(AttachmentDTO::from).toList());
  }

}
