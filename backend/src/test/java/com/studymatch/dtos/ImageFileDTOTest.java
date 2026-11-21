package com.studymatch.dtos;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ImageFileDTOTest {

  @Test
  @DisplayName("A PNG data URL becomes image/png and its bytes")
  void pngDataUrl() {
    // Act
    ImageFileDTO image = ImageFileDTO.fromDataUrl("data:image/png;base64,iVBORw0KGgo=");

    // Assert
    assertEquals("image/png", image.contentType());
    assertArrayEquals(new byte[] { (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A }, image.bytes());
  }

  @Test
  @DisplayName("A JPEG data URL becomes image/jpeg and its bytes")
  void jpegDataUrl() {
    // Act
    ImageFileDTO image = ImageFileDTO.fromDataUrl("data:image/jpeg;base64,/9j/");

    // Assert
    assertEquals("image/jpeg", image.contentType());
    assertArrayEquals(new byte[] { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF }, image.bytes());
  }

}
