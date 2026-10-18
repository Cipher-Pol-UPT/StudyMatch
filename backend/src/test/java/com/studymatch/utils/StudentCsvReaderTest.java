package com.studymatch.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.studymatch.dtos.FieldErrorDTO;
import com.studymatch.dtos.RegisterStudentRequestDTO;
import com.studymatch.exceptions.ErrorCode;
import com.studymatch.exceptions.StudyMatchException;

class StudentCsvReaderTest {

  private static final String HEADER = "name,personalEmail,degreeProgramme,curricularYear\n";

  private final StudentCsvReader reader = new StudentCsvReader();

  private List<StudentCsvReader.Row> read(String csv) {
    return reader.read(csv.getBytes(StandardCharsets.UTF_8));
  }

  private ErrorCode readFails(byte[] file) {
    return assertThrows(StudyMatchException.class, () -> reader.read(file)).getCode();
  }

  private ErrorCode readFails(String csv) {
    return readFails(csv.getBytes(StandardCharsets.UTF_8));
  }

  // what the acceptance criteria ask for

  @Test
  @DisplayName("A normal row becomes a student")
  void normalRow() {
    // Act
    List<StudentCsvReader.Row> rows = read(HEADER + "Ana Test,ana@example.com,Test Degree,2\n");

    // Assert
    assertEquals(1, rows.size());
    assertEquals(new RegisterStudentRequestDTO("Ana Test", "ana@example.com", "Test Degree", 2), rows.get(0).student());
    assertTrue(rows.get(0).errors().isEmpty());
  }

  @Test
  @DisplayName("Quoted fields with commas inside stay one value")
  void quotedComma() {
    List<StudentCsvReader.Row> rows = read(HEADER + "\"Test, Ana\",ana@example.com,\"Degree, With Comma\",1\n");
    assertEquals("Test, Ana", rows.get(0).student().name());
    assertEquals("Degree, With Comma", rows.get(0).student().degreeProgramme());
  }

  @Test
  @DisplayName("A doubled quote inside quotes is a real quote")
  void escapedQuote() {
    List<StudentCsvReader.Row> rows = read(HEADER + "\"Ana \"\"Test\"\" Silva\",ana@example.com,Test Degree,1\n");
    assertEquals("Ana \"Test\" Silva", rows.get(0).student().name());
  }

  @Test
  @DisplayName("UTF-8 names arrive intact")
  void utf8Names() {
    List<StudentCsvReader.Row> rows = read(HEADER + "João Conceição Müller,joao@example.com,Engenharia Informática,1\n");
    assertEquals("João Conceição Müller", rows.get(0).student().name());
    assertEquals("Engenharia Informática", rows.get(0).student().degreeProgramme());
  }

  @Test
  @DisplayName("Rows are numbered like the spreadsheet (header is row 1, empty lines still count)")
  void rowNumbers() {
    // Act - row 3 is empty, like Excel leaves them
    List<StudentCsvReader.Row> rows = read(HEADER + "Ana,ana@example.com,D,1\n,,,\nRui,rui@example.com,D,1\n");

    // Assert
    assertEquals(List.of(2, 4), rows.stream().map(StudentCsvReader.Row::row).toList());
  }

  // what Excel does to files

  @Test
  @DisplayName("Excel's BOM at the start doesn't break the first column")
  void bomIsRemoved() {
    List<StudentCsvReader.Row> rows = read("﻿" + HEADER + "Ana,ana@example.com,D,1\n");
    assertEquals("Ana", rows.get(0).student().name());
  }

  @Test
  @DisplayName("Portuguese Excel's ; separator works too")
  void semicolons() {
    List<StudentCsvReader.Row> rows = read("name;personalEmail;degreeProgramme;curricularYear\nAna;ana@example.com;D;1\n");
    assertEquals("ana@example.com", rows.get(0).student().personalEmail());
  }

  @Test
  @DisplayName("Column names don't care about capitals or order")
  void headerCaseAndOrder() {
    List<StudentCsvReader.Row> rows = read("CurricularYear,NAME,degreeprogramme,PersonalEmail\n3,Ana,D,ana@example.com\n");
    assertEquals(new RegisterStudentRequestDTO("Ana", "ana@example.com", "D", 3), rows.get(0).student());
  }

  @Test
  @DisplayName("Spaces around values are trimmed")
  void valuesTrimmed() {
    List<StudentCsvReader.Row> rows = read(HEADER + "  Ana  , ana@example.com , D , 1 \n");
    assertEquals(new RegisterStudentRequestDTO("Ana", "ana@example.com", "D", 1), rows.get(0).student());
  }

  @Test
  @DisplayName("Windows line endings work")
  void windowsLineEndings() {
    assertEquals(2, read(HEADER.replace("\n", "\r\n") + "Ana,a@example.com,D,1\r\nRui,r@example.com,D,1\r\n").size());
  }

  // problems inside a row (the row is kept, with the error)

  @ParameterizedTest(name = "year \"{0}\" isn't a number")
  @ValueSource(strings = { "two", "1.5", "1º" })
  @DisplayName("A year that isn't a whole number is INVALID on that row")
  void yearNotANumber(String year) {
    // Act
    StudentCsvReader.Row row = read(HEADER + "Ana,ana@example.com,D," + year + "\n").get(0);

    // Assert
    assertNull(row.student().curricularYear());
    assertEquals(List.of(new FieldErrorDTO("curricularYear", "INVALID", Map.of())), row.errors());
  }

  @Test
  @DisplayName("A short line just has empty values at the end")
  void shortLine() {
    StudentCsvReader.Row row = read(HEADER + "Ana,ana@example.com\n").get(0);
    assertEquals(new RegisterStudentRequestDTO("Ana", "ana@example.com", "", null), row.student());
  }

  // problems with the whole file (nothing is imported)

  @ParameterizedTest(name = "\"{0}\"")
  @ValueSource(strings = { "", "   \n  \n" })
  @DisplayName("An empty file is CSV_EMPTY")
  void emptyFile(String csv) {
    assertEquals(ErrorCode.CSV_EMPTY, readFails(csv));
  }

  @Test
  @DisplayName("Only a header (or only empty rows) is CSV_EMPTY")
  void headerOnly() {
    assertEquals(ErrorCode.CSV_EMPTY, readFails(HEADER));
    assertEquals(ErrorCode.CSV_EMPTY, readFails(HEADER + ",,,\n,,,\n"));
  }

  @Test
  @DisplayName("Missing columns are listed by name")
  void missingColumns() {
    // Act
    StudyMatchException e = assertThrows(StudyMatchException.class,
        () -> read("name,personalEmail\nAna,ana@example.com\n"));

    // Assert
    assertEquals(ErrorCode.CSV_MISSING_COLUMNS, e.getCode());
    assertEquals(Map.of("columns", "degreeProgramme, curricularYear"), e.getParams());
  }

  @Test
  @DisplayName("200 rows is the most (boundary: 200 ok)")
  void exactlyMaxRows() {
    assertEquals(StudentCsvReader.MAX_ROWS, read(HEADER + rows(StudentCsvReader.MAX_ROWS)).size());
  }

  @Test
  @DisplayName("201 rows is too many (boundary: 201 fails)")
  void overMaxRows() {
    // Act
    StudyMatchException e = assertThrows(StudyMatchException.class,
        () -> read(HEADER + rows(StudentCsvReader.MAX_ROWS + 1)));

    // Assert
    assertEquals(ErrorCode.CSV_TOO_MANY_ROWS, e.getCode());
    assertEquals(Map.of("max", StudentCsvReader.MAX_ROWS), e.getParams());
  }

  @Test
  @DisplayName("A file that isn't UTF-8 (Excel's plain CSV) is CSV_WRONG_ENCODING instead of garbled names")
  void notUtf8() {
    // Arrange - the same text saved the way Excel's plain "CSV" does
    byte[] windows1252 = (HEADER + "João,joao@example.com,D,1\n").getBytes(Charset.forName("windows-1252"));

    // Act + Assert
    assertEquals(ErrorCode.CSV_WRONG_ENCODING, readFails(windows1252));
  }

  @Test
  @DisplayName("An unclosed quote is CSV_INVALID")
  void unclosedQuote() {
    assertEquals(ErrorCode.CSV_INVALID, readFails(HEADER + "\"Ana,ana@example.com,D,1\n"));
  }

  @Test
  @DisplayName("The same column twice is CSV_INVALID")
  void repeatedColumn() {
    assertEquals(ErrorCode.CSV_INVALID, readFails("name,name,personalEmail,degreeProgramme,curricularYear\nA,B,a@example.com,D,1\n"));
  }

  // template

  @Test
  @DisplayName("The template is only the four columns, with the BOM for Excel")
  void template() {
    assertEquals("﻿name,personalEmail,degreeProgramme,curricularYear\r\n", reader.template());
  }

  @Test
  @DisplayName("The template can be read back (once filled in)")
  void templateRoundTrip() {
    assertEquals(1, read(reader.template() + "Ana,ana@example.com,D,1\r\n").size());
  }

  private static String rows(int count) {
    StringBuilder csv = new StringBuilder();
    for (int i = 0; i < count; i++) {
      csv.append("Student ").append(i).append(",s").append(i).append("@example.com,D,1\n");
    }
    return csv.toString();
  }

}
