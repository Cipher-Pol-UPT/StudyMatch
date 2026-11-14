package com.studymatch.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;

import com.studymatch.dtos.FieldErrorDTO;
import com.studymatch.dtos.RegisterStudentRequestDTO;

// only turns the file into rows, deciding if a row is valid is StudentService's job
@Component
public class StudentCsvReader {

  public static final List<String> COLUMNS = List.of("name", "personalEmail", "degreeProgramme", "curricularYear");
  public static final int MAX_ROWS = CsvFiles.MAX_ROWS;

  // row is the line in the spreadsheet (the header is row 1), errors are the ones found while reading
  public record Row(int row, RegisterStudentRequestDTO student, List<FieldErrorDTO> errors) {
  }

  public String template() {
    return CsvFiles.template(COLUMNS);
  }

  public List<Row> read(byte[] file) {
    return CsvFiles.read(file, COLUMNS).stream()
        .map(line -> toRow(line.row(), line.record()))
        .toList();
  }

  private static Row toRow(int row, CSVRecord record) {
    List<FieldErrorDTO> errors = new ArrayList<>();
    String year = CsvFiles.value(record, "curricularYear");
    Integer curricularYear = null;
    if (!year.isBlank()) {
      try {
        curricularYear = Integer.valueOf(year);
      } catch (NumberFormatException e) {
        errors.add(new FieldErrorDTO("curricularYear", "INVALID", Map.of()));
      }
    }

    RegisterStudentRequestDTO student = new RegisterStudentRequestDTO(CsvFiles.value(record, "name"),
        CsvFiles.value(record, "personalEmail"), CsvFiles.value(record, "degreeProgramme"), curricularYear);
    return new Row(row, student, errors);
  }

}
