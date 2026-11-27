package com.studymatch.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.studymatch.exceptions.ErrorCode;
import com.studymatch.exceptions.StudyMatchException;

class GroupCsvReaderTest {

  private static final String HEADER = "groupNumber,studentNumber\n";

  private final GroupCsvReader reader = new GroupCsvReader();

  private List<GroupCsvReader.Row> read(String csv) {
    return reader.read(csv.getBytes(StandardCharsets.UTF_8));
  }

  @Test
  @DisplayName("An exported file reads back as group and student numbers, the name and email are ignored")
  void exportedFile() {
    // Arrange - what export groups writes
    byte[] exported = new GroupCsvWriter().write(List.of(new GroupCsvWriter.Row(1, 50001L, "Ana", "a@uni.pt"),
        new GroupCsvWriter.Row(2, 50002L, "Rui", null)));

    // Act
    List<GroupCsvReader.Row> rows = reader.read(exported);

    // Assert
    assertEquals(List.of(new GroupCsvReader.Row(2, 1, 50001L), new GroupCsvReader.Row(3, 2, 50002L)), rows);
  }

  // the other imports stop at 200 rows, the export doesn't: 201 is one past their limit
  @Test
  @DisplayName("An export of more than 200 students still reads back, up to the groups' own limit")
  void moreThanTheOtherImports() {
    // Arrange
    List<GroupCsvWriter.Row> students = new ArrayList<>();
    for (int i = 0; i < CsvFiles.MAX_ROWS + 1; i++) {
      students.add(new GroupCsvWriter.Row(i / 3 + 1, 50001L + i, "Student " + i, (50001 + i) + "@uni.pt"));
    }

    // Act
    List<GroupCsvReader.Row> rows = reader.read(new GroupCsvWriter().write(students));

    // Assert
    assertEquals(CsvFiles.MAX_ROWS + 1, rows.size());
    assertEquals(new GroupCsvReader.Row(CsvFiles.MAX_ROWS + 2, 67, 50201L), rows.getLast());
  }

  @Test
  @DisplayName("One row past the groups' limit is CSV_TOO_MANY_ROWS with the limit")
  void tooManyRows() {
    // Arrange
    StringBuilder csv = new StringBuilder(HEADER);
    for (int i = 0; i < GroupCsvReader.MAX_ROWS + 1; i++) {
      csv.append("1,").append(50001 + i).append('\n');
    }

    // Act
    StudyMatchException e = assertThrows(StudyMatchException.class, () -> read(csv.toString()));

    // Assert
    assertEquals(ErrorCode.CSV_TOO_MANY_ROWS, e.getCode());
    assertEquals(Map.of("max", GroupCsvReader.MAX_ROWS), e.getParams());
  }

  @Test
  @DisplayName("Semicolons and only the two required columns are fine")
  void semicolonsAndRequiredColumnsOnly() {
    // Act
    List<GroupCsvReader.Row> rows = read("studentNumber;groupNumber\n50001;3\n");

    // Assert
    assertEquals(List.of(new GroupCsvReader.Row(2, 3, 50001L)), rows);
  }

  // 1 is the smallest valid number: test 0 and -1 below it
  @ParameterizedTest
  @ValueSource(strings = { "abc", "1.5", "0", "-1" })
  @DisplayName("A group or student number that isn't a positive whole number comes out as null")
  void invalidNumbersAreNull(String value) {
    // Act
    GroupCsvReader.Row row = read(HEADER + value + "," + value + "\n").getFirst();

    // Assert
    assertEquals(2, row.row());
    assertNull(row.groupNumber());
    assertNull(row.studentNumber());
  }

  @Test
  @DisplayName("An empty group number next to a student number is null")
  void emptyGroupNumberIsNull() {
    // Act
    GroupCsvReader.Row row = read(HEADER + ",50001\n").getFirst();

    // Assert
    assertNull(row.groupNumber());
    assertEquals(50001L, row.studentNumber());
  }

  @Test
  @DisplayName("Without studentNumber the file is CSV_MISSING_COLUMNS naming it")
  void missingColumn() {
    // Act
    StudyMatchException e = assertThrows(StudyMatchException.class, () -> read("groupNumber,name\n1,Ana\n"));

    // Assert
    assertEquals(ErrorCode.CSV_MISSING_COLUMNS, e.getCode());
    assertEquals(Map.of("columns", "studentNumber"), e.getParams());
  }

  @Test
  @DisplayName("The template has the export's four columns")
  void template() {
    // Act + Assert
    assertEquals((char) 0xFEFF + "groupNumber,studentNumber,name,institutionalEmail\r\n", reader.template());
  }

}
