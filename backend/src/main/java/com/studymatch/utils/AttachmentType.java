package com.studymatch.utils;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;

// the files a message can have (GR10). never leaves the backend, the frontend gets the content type
public enum AttachmentType {

  PNG("image/png", "png"),
  JPEG("image/jpeg", "jpg", "jpeg"),
  GIF("image/gif", "gif"),
  WEBP("image/webp", "webp"),
  PDF("application/pdf", "pdf"),
  DOCX("application/vnd.openxmlformats-officedocument.wordprocessingml.document", "docx"),
  XLSX("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "xlsx"),
  PPTX("application/vnd.openxmlformats-officedocument.presentationml.presentation", "pptx"),
  TXT("text/plain;charset=UTF-8", "txt");

  private final String contentType;
  private final List<String> extensions;

  AttachmentType(String contentType, String... extensions) {
    this.contentType = contentType;
    this.extensions = List.of(extensions);
  }

  // by the extension, what the browser says it is can be anything
  public static Optional<AttachmentType> ofName(String fileName) {
    String extension = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    return fileName.contains(".")
        ? Stream.of(values()).filter(type -> type.extensions.contains(extension)).findFirst()
        : Optional.empty();
  }

  public String getContentType() {
    return contentType;
  }

}
