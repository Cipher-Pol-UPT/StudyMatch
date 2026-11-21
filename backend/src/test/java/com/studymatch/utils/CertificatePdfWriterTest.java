package com.studymatch.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.studymatch.enums.Achievement;
import com.studymatch.models.CertificateEvidence;
import com.studymatch.utils.CertificatePdfWriter.Entry;
import com.studymatch.utils.CertificatePdfWriter.Holder;

class CertificatePdfWriterTest {

  private static final Locale EN = Locale.forLanguageTag("en-GB");
  private static final Locale PT = Locale.forLanguageTag("pt-PT");
  private static final LocalDate ISSUED = LocalDate.of(2026, 11, 20);

  private CertificatePdfWriter writer;
  private Holder holder;
  private CertificateEvidence evidence;

  @BeforeEach
  void setUp() {
    writer = new CertificatePdfWriter("http://test.studymatch.com/");
    holder = new Holder("Test Student", 50940L, "Test Degree");
    evidence = new CertificateEvidence(12L, 15L, 4.8, "Test Subject", ISSUED);
  }

  private static PDDocument read(byte[] pdf) throws IOException {
    return Loader.loadPDF(pdf);
  }

  private static String text(PDDocument document) throws IOException {
    return new PDFTextStripper().getText(document).replaceAll("\\s+", " ");
  }

  @Test
  @DisplayName("A certificate is one landscape page with the student, the achievement, the evidence and the verify link")
  void certificate() throws IOException {
    // Act
    byte[] pdf = writer.writeCertificate(holder, new Entry(Achievement.MENTOR, LocalDate.of(2026, 2, 17), evidence),
        "code-1", ISSUED, EN);

    // Assert
    try (PDDocument document = read(pdf)) {
      assertEquals(1, document.getNumberOfPages());
      PDRectangle page = document.getPage(0).getMediaBox();
      assertTrue(page.getWidth() > page.getHeight());
      String text = text(document);
      assertTrue(text.contains("Certificate of Achievement"));
      assertTrue(text.contains("Test Student"));
      assertTrue(text.contains("50940"));
      assertTrue(text.contains("Test Degree"));
      assertTrue(text.contains("Mentor"));
      assertTrue(text.contains("Tutored 15 different students."));
      assertTrue(text.contains("4.8"));
      assertTrue(text.contains("Test Subject"));
      assertTrue(text.contains("17 February 2026"));
      assertTrue(text.contains("test.studymatch.com/verify/code-1"));
    }
  }

  @Test
  @DisplayName("Without evidence the evidence card isn't there")
  void certificateWithoutEvidence() throws IOException {
    // Act
    byte[] pdf = writer.writeCertificate(holder, new Entry(Achievement.FIRST_STUDYROOM, ISSUED, null), "code-1", ISSUED, EN);

    // Assert
    try (PDDocument document = read(pdf)) {
      assertFalse(text(document).contains("Evidence"));
    }
  }

  @Test
  @DisplayName("A certificate in Portuguese uses the Portuguese texts and dates")
  void certificateInPortuguese() throws IOException {
    // Act
    byte[] pdf = writer.writeCertificate(holder, new Entry(Achievement.TOP_RATED, LocalDate.of(2026, 2, 17), evidence),
        "code-1", ISSUED, PT);

    // Assert
    try (PDDocument document = read(pdf)) {
      String text = text(document);
      assertTrue(text.contains("Certificado de Conquista"));
      assertTrue(text.contains("Bem avaliado"));
      assertTrue(text.contains("17 de fevereiro de 2026"));
      assertTrue(text.contains("4,8"));
    }
  }

  @Test
  @DisplayName("A record is one portrait page listing every achievement chosen")
  void record() throws IOException {
    // Arrange
    List<Entry> entries = List.of(new Entry(Achievement.FIRST_STUDYROOM, LocalDate.of(2025, 11, 6), null),
        new Entry(Achievement.MENTOR, LocalDate.of(2026, 2, 17), evidence),
        new Entry(Achievement.TOP_RATED, LocalDate.of(2026, 5, 28), null));

    // Act
    byte[] pdf = writer.writeRecord(holder, entries, "record-1", ISSUED, EN);

    // Assert
    try (PDDocument document = read(pdf)) {
      assertEquals(1, document.getNumberOfPages());
      PDRectangle page = document.getPage(0).getMediaBox();
      assertTrue(page.getHeight() > page.getWidth());
      String text = text(document);
      assertTrue(text.contains("Record of Achievements"));
      assertTrue(text.contains("3 of 4 achievements"));
      assertTrue(text.contains("First StudyRoom"));
      assertTrue(text.contains("Mentor"));
      assertTrue(text.contains("Top rated"));
      assertTrue(text.contains("test.studymatch.com/verify/record-1"));
    }
  }

}
