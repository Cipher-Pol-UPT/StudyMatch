package com.studymatch.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.studymatch.exceptions.ErrorCode;
import com.studymatch.exceptions.StudyMatchException;

class EnrolmentCsvReaderTest {

  private static final String HEADER = "studentNumber\n";
  private static final char BOM = (char) 0xFEFF;

  private final EnrolmentCsvReader reader = new EnrolmentCsvReader();

  private List<EnrolmentCsvReader.Row> read(String csv) {
    return reader.read(csv.getBytes(StandardCharsets.UTF_8));
  }

  @Test
  @DisplayName("Each line becomes a number with its spreadsheet row, blank lines are skipped")
  void numbersWithRows() {
    // Act
    List<EnrolmentCsvReader.Row> rows = read(HEADER + "50001\n\n 50002 \n");

    // Assert - the header is row 1, the blank line was row 3
    assertEquals(List.of(new EnrolmentCsvReader.Row(2, 50001L), new EnrolmentCsvReader.Row(4, 50002L)), rows);
  }

  @ParameterizedTest
  @ValueSource(strings = { "abc", "50001.5", "5O001" })
  @DisplayName("A value that isn't a whole number comes out as null")
  void notANumberIsNull(String value) {
    // Act
    EnrolmentCsvReader.Row row = read(HEADER + value + "\n").getFirst();

    // Assert
    assertEquals(2, row.row());
    assertNull(row.number());
  }

  @Test
  @DisplayName("Extra columns are fine, an empty number next to them is null")
  void emptyNumberIsNull() {
    // Act
    List<EnrolmentCsvReader.Row> rows = read("studentNumber,name\n,Ana\n");

    // Assert
    assertNull(rows.getFirst().number());
  }

  @Test
  @DisplayName("A file without the studentNumber column gets CSV_MISSING_COLUMNS")
  void missingColumn() {
    // Act
    StudyMatchException e = assertThrows(StudyMatchException.class, () -> read("number\n50001\n"));

    // Assert
    assertEquals(ErrorCode.CSV_MISSING_COLUMNS, e.getCode());
  }

  @Test
  @DisplayName("Excel's BOM at the start doesn't break the header")
  void bomIsIgnored() {
    // Act
    List<EnrolmentCsvReader.Row> rows = read(BOM + HEADER + "50001\n");

    // Assert
    assertEquals(50001L, rows.getFirst().number());
  }

  @Test
  @DisplayName("The template is only the studentNumber column, with the BOM for Excel")
  void template() {
    // Act
    String template = reader.template();

    // Assert
    assertEquals(BOM, template.charAt(0));
    assertEquals("studentNumber\r\n", template.substring(1));
  }

}
