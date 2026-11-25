package com.studymatch.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class AttachmentTypeTest {

  @ParameterizedTest
  @CsvSource({
      "a.png, PNG",
      "a.jpg, JPEG",
      "a.jpeg, JPEG",
      "a.gif, GIF",
      "a.webp, WEBP",
      "a.pdf, PDF",
      "a.docx, DOCX",
      "a.xlsx, XLSX",
      "a.pptx, PPTX",
      "a.txt, TXT",
      "REPORT.PDF, PDF",
      "my.notes.v2.txt, TXT",
      ".png, PNG" })
  @DisplayName("The type comes from the last extension, in any capitals")
  void byExtension(String name, AttachmentType expected) {
    // Act
    Optional<AttachmentType> type = AttachmentType.ofName(name);

    // Assert
    assertEquals(Optional.of(expected), type);
  }

  @ParameterizedTest
  @ValueSource(strings = { "", "png", "a.", "a.svg", "a.html", "a.exe", "a.doc", "a.docm", "a.zip", "a.png.exe", "a.pdf " })
  @DisplayName("No extension, or one that isn't allowed, has no type (GR10)")
  void notAllowed(String name) {
    // Act
    Optional<AttachmentType> type = AttachmentType.ofName(name);

    // Assert
    assertEquals(Optional.empty(), type);
  }

}
