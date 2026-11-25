package com.studymatch.dtos;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;

import com.studymatch.models.AttachmentFile;

// an attachment being opened or downloaded
public record AttachmentFileDTO(String name, String contentType, Resource content) {

  public static AttachmentFileDTO from(AttachmentFile file) {
    return new AttachmentFileDTO(file.getAttachment().getName(), file.getAttachment().getContentType(),
        new ByteArrayResource(file.getBytes()));
  }

  // shown in the chat, everything else is a download
  public boolean isImage() {
    return contentType.startsWith("image/");
  }

}
