package com.studymatch.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class AttachmentFilesTest {

  // marks that turn the text after them around, "fdp" shows as "pdf"
  private static final String RIGHT_TO_LEFT = String.valueOf((char) 0x202E);

  private static final String SMILE = Character.toString(0x1F600);

  private static byte[] zip(String... parts) throws IOException {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
      for (String part : parts) {
        zip.putNextEntry(new ZipEntry(part));
        zip.write("<xml/>".getBytes(StandardCharsets.UTF_8));
      }
    }
    return bytes.toByteArray();
  }

  private static byte[] text(String text) {
    return text.getBytes(StandardCharsets.UTF_8);
  }

  // signatures

  @ParameterizedTest
  @CsvSource({
      "PNG, 89504e470d0a1a0a",
      "JPEG, ffd8ff",
      "GIF, 474946383761",
      "GIF, 474946383961",
      "WEBP, 524946460000000057454250",
      "PDF, 255044462d" })
  @DisplayName("The right first bytes match, one missing or changed doesn't")
  void signature(AttachmentType type, String hex) {
    // Arrange
    byte[] signature = HexFormat.of().parseHex(hex);
    byte[] cut = Arrays.copyOf(signature, signature.length - 1);
    byte[] changed = signature.clone();
    changed[0]++;

    // Act + Assert
    assertTrue(AttachmentFiles.matches(type, signature));
    assertTrue(AttachmentFiles.matches(type, Arrays.copyOf(signature, 100)));
    assertFalse(AttachmentFiles.matches(type, cut));
    assertFalse(AttachmentFiles.matches(type, changed));
  }

  @ParameterizedTest
  @EnumSource(AttachmentType.class)
  @DisplayName("An empty file is never what it says")
  void empty(AttachmentType type) {
    // Act + Assert
    assertFalse(AttachmentFiles.matches(type, new byte[0]));
  }

  @Test
  @DisplayName("Another RIFF file, like a WAV, isn't a WebP")
  void riffIsNotEnough() {
    // Act + Assert
    assertFalse(AttachmentFiles.matches(AttachmentType.WEBP, text("RIFF0000WAVE")));
  }

  // Office files

  @ParameterizedTest
  @CsvSource({ "DOCX, word/document.xml", "XLSX, xl/workbook.xml", "PPTX, ppt/presentation.xml" })
  @DisplayName("An Office file is a zip with its own main part")
  void office(AttachmentType type, String mainPart) throws IOException {
    // Act + Assert
    assertTrue(AttachmentFiles.matches(type, zip("[Content_Types].xml", mainPart)));
    assertFalse(AttachmentFiles.matches(type, zip("[Content_Types].xml")));
    assertFalse(AttachmentFiles.matches(type, zip(mainPart)));
  }

  @ParameterizedTest
  @EnumSource(value = AttachmentType.class, names = { "XLSX", "PPTX" })
  @DisplayName("A Word file renamed to another Office type doesn't match")
  void renamedOffice(AttachmentType type) throws IOException {
    // Act + Assert
    assertFalse(AttachmentFiles.matches(type, zip("[Content_Types].xml", "word/document.xml")));
  }

  @Test
  @DisplayName("A zip cut short isn't an Office file")
  void damagedOffice() throws IOException {
    // Arrange
    byte[] whole = zip("[Content_Types].xml", "word/document.xml");

    // Act + Assert
    assertFalse(AttachmentFiles.matches(AttachmentType.DOCX, Arrays.copyOf(whole, whole.length / 2)));
  }

  @Test
  @DisplayName("Something else with an Office file after it isn't one, it has to start like a zip")
  void officeInsideAnother() throws IOException {
    // Arrange
    ByteArrayOutputStream both = new ByteArrayOutputStream();
    both.write(text("<script>"));
    both.write(zip("[Content_Types].xml", "word/document.xml"));

    // Act + Assert
    assertFalse(AttachmentFiles.matches(AttachmentType.DOCX, both.toByteArray()));
  }

  // plain text

  @ParameterizedTest
  @ValueSource(strings = { "hello", "olá, até já", "a\tb\r\nc\n", " " })
  @DisplayName("Plain text is UTF-8 with tabs and line ends")
  void plainText(String content) {
    // Act + Assert
    assertTrue(AttachmentFiles.matches(AttachmentType.TXT, text(content)));
  }

  @Test
  @DisplayName("Text in another encoding, or with control characters, isn't plain text")
  void notPlainText() {
    // Act + Assert
    assertFalse(AttachmentFiles.matches(AttachmentType.TXT, "olá".getBytes(StandardCharsets.ISO_8859_1)));
    assertFalse(AttachmentFiles.matches(AttachmentType.TXT, "hello".getBytes(StandardCharsets.UTF_16)));
    assertFalse(AttachmentFiles.matches(AttachmentType.TXT, new byte[] { 'a', 0, 'b' }));
    assertFalse(AttachmentFiles.matches(AttachmentType.TXT, new byte[] { 'a', 0x1F, 'b' }));
  }

  // names

  @ParameterizedTest
  @CsvSource({
      "notes.txt, notes.txt",
      "C:\\Users\\ana\\notes.txt, notes.txt",
      "/home/ana/notes.txt, notes.txt",
      "../../notes.txt, notes.txt",
      "'  notes.txt  ', notes.txt",
      "resumo de cálculo.pdf, resumo de cálculo.pdf" })
  @DisplayName("A name loses its folders and the spaces around it")
  void foldersGo(String name, String expected) {
    // Act + Assert
    assertEquals(expected, AttachmentFiles.cleanName(name));
  }

  @Test
  @DisplayName("A name loses what can't be seen, an emoji stays")
  void invisibleGoes() {
    // Arrange
    String name = "a" + (char) 0 + RIGHT_TO_LEFT + "b\r\n" + SMILE + ".txt" + (char) 0xD800;

    // Act + Assert
    assertEquals("ab" + SMILE + ".txt", AttachmentFiles.cleanName(name));
  }

  @Test
  @DisplayName("A name at the limit stays whole, one over loses a letter and keeps its extension")
  void nameLimit() {
    // Act + Assert
    assertEquals("a".repeat(251) + ".txt", AttachmentFiles.cutName("a".repeat(251) + ".txt", 255));
    assertEquals("a".repeat(251) + ".txt", AttachmentFiles.cutName("a".repeat(252) + ".txt", 255));
  }

  @Test
  @DisplayName("A long name isn't cut through the middle of an emoji")
  void emojiIsNotCut() {
    // Act + Assert
    assertEquals("a".repeat(250) + ".txt", AttachmentFiles.cutName("a".repeat(250) + SMILE + ".txt", 255));
  }

}
