package com.studymatch.utils;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GroupPdfWriterTest {

  private static final Locale EN = Locale.forLanguageTag("en-GB");
  private static final Locale PT = Locale.forLanguageTag("pt-PT");

  private final GroupPdfWriter writer = new GroupPdfWriter();

  private static final List<GroupCsvWriter.Row> ROWS = List.of(
      new GroupCsvWriter.Row(1, 50001L, "Ana", "50001@students.studymatch.com"),
      new GroupCsvWriter.Row(1, 50002L, "Rui", "50002@students.studymatch.com"),
      new GroupCsvWriter.Row(2, 50003L, "Bia & Co <b>", null));

  private static String text(byte[] pdf) throws IOException {
    try (PDDocument document = Loader.loadPDF(pdf)) {
      return new PDFTextStripper().getText(document).replaceAll("\\s+", " ");
    }
  }

  @Test
  @DisplayName("The subject and assignment on top, then each group with its students' numbers, names and emails")
  void groupsWithMembers() throws IOException {
    // Act
    String text = text(writer.write("Test Subject", "Project", ROWS, EN));

    // Assert
    assertTrue(text.contains("Groups"));
    assertTrue(text.contains("Test Subject"));
    assertTrue(text.contains("Project"));
    assertTrue(text.contains("Group 1 2 students"));
    assertTrue(text.contains("Group 2 1 student"));
    // the column headings are upper case on the page
    assertTrue(text.contains("STUDENT NUMBER NAME INSTITUTIONAL EMAIL"));
    assertTrue(text.contains("50001 Ana 50001@students.studymatch.com"));
    assertTrue(text.contains("50002 Rui 50002@students.studymatch.com"));
    assertTrue(text.indexOf("Group 1") < text.indexOf("Group 2"));
  }

  @Test
  @DisplayName("Names are written as text, not read as markup")
  void escapesNames() throws IOException {
    // Act + Assert
    assertTrue(text(writer.write("Test Subject", "Project", ROWS, EN)).contains("50003 Bia & Co <b>"));
  }

  @Test
  @DisplayName("In Portuguese the headings are translated")
  void portuguese() throws IOException {
    // Act
    String text = text(writer.write("Test Subject", "Project", ROWS, PT));

    // Assert
    assertTrue(text.contains("Grupos"));
    assertTrue(text.contains("Grupo 1 2 estudantes"));
    assertTrue(text.contains("NÚMERO DE ESTUDANTE NOME EMAIL INSTITUCIONAL"));
  }

  @Test
  @DisplayName("A group number past 999 isn't printed with a thousands separator")
  void plainGroupNumber() throws IOException {
    // Act + Assert
    assertTrue(text(writer.write("Test Subject", "Project",
        List.of(new GroupCsvWriter.Row(1000, 50001L, "Ana", null)), EN)).contains("Group 1000"));
  }

}
