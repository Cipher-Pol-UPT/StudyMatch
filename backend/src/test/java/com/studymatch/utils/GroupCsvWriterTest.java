package com.studymatch.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GroupCsvWriterTest {

  private static final char BOM = (char) 0xFEFF;

  private final GroupCsvWriter writer = new GroupCsvWriter();

  private String write(GroupCsvWriter.Row... rows) {
    return new String(writer.write(List.of(rows)), StandardCharsets.UTF_8);
  }

  @Test
  @DisplayName("The header and then one line per student, in the order given")
  void headerAndRows() {
    // Act
    String csv = write(new GroupCsvWriter.Row(1, 50001L, "Ana", "50001@students.studymatch.com"),
        new GroupCsvWriter.Row(1, 50002L, "Rui", "50002@students.studymatch.com"),
        new GroupCsvWriter.Row(2, 50003L, "Bia", "50003@students.studymatch.com"));

    // Assert
    assertEquals(BOM + "groupNumber,studentNumber,name,institutionalEmail\r\n"
        + "1,50001,Ana,50001@students.studymatch.com\r\n"
        + "1,50002,Rui,50002@students.studymatch.com\r\n"
        + "2,50003,Bia,50003@students.studymatch.com\r\n", csv);
  }

  @Test
  @DisplayName("A name with a comma or quotes is quoted, so it stays one column")
  void quotesNames() {
    // Act
    String csv = write(new GroupCsvWriter.Row(1, 50001L, "Silva, \"Ana\"", "50001@students.studymatch.com"));

    // Assert
    assertTrue(csv.endsWith("1,50001,\"Silva, \"\"Ana\"\"\",50001@students.studymatch.com\r\n"));
  }

  @Test
  @DisplayName("No institutional email yet is an empty column, not \"null\"")
  void missingEmailIsEmpty() {
    // Act + Assert
    assertTrue(write(new GroupCsvWriter.Row(3, 50001L, "Ana", null)).endsWith("\r\n3,50001,Ana,\r\n"));
  }

  @Test
  @DisplayName("What it writes, the CSV import reads back with the same columns")
  void readsBack() {
    // Act
    byte[] file = writer.write(List.of(new GroupCsvWriter.Row(2, 50001L, "João", "50001@students.studymatch.com")));

    // Assert
    CsvFiles.Line line = CsvFiles.read(file, GroupCsvWriter.COLUMNS).getFirst();
    assertEquals(2, line.row());
    assertEquals("2", CsvFiles.value(line.record(), "groupNumber"));
    assertEquals("50001", CsvFiles.value(line.record(), "studentNumber"));
    assertEquals("João", CsvFiles.value(line.record(), "name"));
    assertEquals("50001@students.studymatch.com", CsvFiles.value(line.record(), "institutionalEmail"));
  }

}
