package com.studymatch.dtos;

import com.studymatch.models.Attachment;

// a message's file without what's in it, size is in bytes. it opens at
// /chats/{chatId}/messages/{messageId}/attachments/{id}
public record AttachmentDTO(Long id, String name, String contentType, int size) {

  public static AttachmentDTO from(Attachment attachment) {
    return new AttachmentDTO(attachment.getId(), attachment.getName(), attachment.getContentType(),
        attachment.getSize());
  }

}
