package com.studymatch.dtos;

import java.util.Base64;

// an image sent as a file instead of base64 in JSON, so the browser can use it as <img src> and cache it
public record ImageFileDTO(String contentType, byte[] bytes) {

  // the data URL was checked when it was saved, "data:image/png;base64,..." here
  public static ImageFileDTO fromDataUrl(String dataUrl) {
    String contentType = dataUrl.substring("data:".length(), dataUrl.indexOf(';'));
    byte[] bytes = Base64.getDecoder().decode(dataUrl.substring(dataUrl.indexOf(',') + 1));
    return new ImageFileDTO(contentType, bytes);
  }

}
