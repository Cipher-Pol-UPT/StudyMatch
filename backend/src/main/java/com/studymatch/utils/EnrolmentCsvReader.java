package com.studymatch.utils;

import java.util.List;

import org.springframework.stereotype.Component;

// only turns the file into student numbers, a value that isn't a number comes out as null
@Component
public class EnrolmentCsvReader {

  public static final List<String> COLUMNS = List.of("studentNumber");

  // row is the line in the spreadsheet (the header is row 1)
  public record Row(int row, Long number) {
  }

  public String template() {
    return CsvFiles.template(COLUMNS);
  }

  public List<Row> read(byte[] file) {
    return CsvFiles.read(file, COLUMNS).stream()
        .map(line -> new Row(line.row(), toNumber(CsvFiles.value(line.record(), "studentNumber"))))
        .toList();
  }

  private static Long toNumber(String value) {
    try {
      return Long.valueOf(value);
    } catch (NumberFormatException e) {
      return null;
    }
  }

}
