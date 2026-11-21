package com.studymatch.dtos;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

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

  @Test
  @DisplayName("Two images with the same type and bytes are equal, even as different arrays")
  void equalByContent() {
    // Arrange
    ImageFileDTO image = new ImageFileDTO("image/png", new byte[] { 1, 2, 3 });
    ImageFileDTO same = new ImageFileDTO("image/png", new byte[] { 1, 2, 3 });

    // Act + Assert
    assertEquals(image, same);
    assertEquals(image.hashCode(), same.hashCode());
  }

  @Test
  @DisplayName("Other bytes, another type or something that isn't an image: not equal")
  void notEqual() {
    // Arrange
    ImageFileDTO image = new ImageFileDTO("image/png", new byte[] { 1, 2, 3 });

    // Act + Assert
    assertNotEquals(image, new ImageFileDTO("image/png", new byte[] { 1, 2, 4 }));
    assertNotEquals(image, new ImageFileDTO("image/jpeg", new byte[] { 1, 2, 3 }));
    assertNotEquals(image, "image/png");
  }

  @Test
  @DisplayName("toString shows the size, not the bytes")
  void toStringShowsSize() {
    // Arrange
    ImageFileDTO image = new ImageFileDTO("image/png", new byte[1024]);

    // Act + Assert
    assertEquals("ImageFileDTO[contentType=image/png, bytes=1024]", image.toString());
  }

}
