package com.studymatch.utils;

import java.util.List;

import org.springframework.stereotype.Component;

// import groups: only turns the file into numbers, a value that isn't a positive number comes out as null.
// name and institutionalEmail can be there (an exported file has them) but are never read
@Component
public class GroupCsvReader {

  public static final List<String> COLUMNS = List.of("groupNumber", "studentNumber");

  // the export has no limit, so this has to fit a whole edition, far more than any has. the 1 MB still applies
  public static final int MAX_ROWS = 10_000;

  // row is the line in the spreadsheet (the header is row 1)
  public record Row(int row, Integer groupNumber, Long studentNumber) {
  }

  // the export's columns, so a teacher filling it in by hand sees the same file they'd export
  public String template() {
    return CsvFiles.template(GroupCsvWriter.COLUMNS);
  }

  public List<Row> read(byte[] file) {
    return CsvFiles.read(file, COLUMNS, MAX_ROWS).stream()
        .map(line -> new Row(line.row(), toGroupNumber(CsvFiles.value(line.record(), "groupNumber")),
            toStudentNumber(CsvFiles.value(line.record(), "studentNumber"))))
        .toList();
  }

  private static Integer toGroupNumber(String value) {
    try {
      int number = Integer.parseInt(value);
      return number >= 1 ? number : null;
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private static Long toStudentNumber(String value) {
    try {
      long number = Long.parseLong(value);
      return number >= 1 ? number : null;
    } catch (NumberFormatException e) {
      return null;
    }
  }

}
