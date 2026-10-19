package com.studymatch.utils;

import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.csv.DuplicateHeaderMode;
import org.springframework.stereotype.Component;

import com.studymatch.dtos.FieldErrorDTO;
import com.studymatch.dtos.RegisterStudentRequestDTO;
import com.studymatch.exceptions.ErrorCode;
import com.studymatch.exceptions.StudyMatchException;

// only turns the file into rows, deciding if a row is valid is StudentService's job
@Component
public class StudentCsvReader {

  public static final List<String> COLUMNS = List.of("name", "personalEmail", "degreeProgramme", "curricularYear");
  public static final int MAX_ROWS = 200;

  // the invisible mark Excel puts at the start of UTF-8 files
  private static final String BOM = "\uFEFF";

  // row is the line in the spreadsheet (the header is row 1), errors are the ones found while reading
  public record Row(int row, RegisterStudentRequestDTO student, List<FieldErrorDTO> errors) {
  }

  // starts with the BOM so Excel opens (and saves) it as UTF-8
  public String template() {
    return BOM + String.join(",", COLUMNS) + "\r\n";
  }

  public List<Row> read(byte[] file) {
    String text = decodeUtf8(file);
    if (text.startsWith(BOM)) {
      text = text.substring(1); // otherwise the first column would be read as BOM + "name"
    }
    if (text.isBlank()) {
      throw new StudyMatchException(ErrorCode.CSV_EMPTY);
    }

    CSVFormat format = CSVFormat.DEFAULT.builder()
        .setDelimiter(guessDelimiter(text))
        .setHeader()
        .setSkipHeaderRecord(true)
        .setIgnoreHeaderCase(true)
        .setDuplicateHeaderMode(DuplicateHeaderMode.DISALLOW) // two "name" columns, which one is right?
        .setTrim(true)
        .get();

    List<Row> rows = new ArrayList<>();
    try (CSVParser parser = CSVParser.parse(new StringReader(text), format)) {
      checkColumns(parser.getHeaderNames());

      int row = 1;
      for (CSVRecord record : parser) {
        row++;
        if (isBlank(record)) {
          continue; // Excel likes to leave empty ",,," lines at the end
        }
        if (rows.size() == MAX_ROWS) {
          throw new StudyMatchException(ErrorCode.CSV_TOO_MANY_ROWS, Map.of("max", MAX_ROWS));
        }
        rows.add(toRow(row, record));
      }
    } catch (IOException | UncheckedIOException | IllegalArgumentException | IllegalStateException e) {
      // unclosed quotes, repeated column names...
      throw new StudyMatchException(ErrorCode.CSV_INVALID);
    }

    if (rows.isEmpty()) {
      throw new StudyMatchException(ErrorCode.CSV_EMPTY);
    }
    return rows;
  }

  // strict on purpose: Excel's plain "CSV" isn't UTF-8 and would turn João into JoÃ£o without anyone noticing
  private static String decodeUtf8(byte[] file) {
    try {
      return StandardCharsets.UTF_8.newDecoder()
          .onMalformedInput(CodingErrorAction.REPORT)
          .onUnmappableCharacter(CodingErrorAction.REPORT)
          .decode(ByteBuffer.wrap(file))
          .toString();
    } catch (CharacterCodingException e) {
      throw new StudyMatchException(ErrorCode.CSV_WRONG_ENCODING);
    }
  }

  // Excel in Portuguese saves with ; instead of ,
  private static char guessDelimiter(String text) {
    String header = text.lines().findFirst().orElse("");
    long semicolons = header.chars().filter(c -> c == ';').count();
    long commas = header.chars().filter(c -> c == ',').count();
    return semicolons > commas ? ';' : ',';
  }

  private static void checkColumns(List<String> headerNames) {
    List<String> missing = COLUMNS.stream()
        .filter(column -> headerNames.stream().noneMatch(column::equalsIgnoreCase))
        .toList();
    if (!missing.isEmpty()) {
      throw new StudyMatchException(ErrorCode.CSV_MISSING_COLUMNS, Map.of("columns", String.join(", ", missing)));
    }
  }

  private static boolean isBlank(CSVRecord record) {
    return record.stream().allMatch(String::isBlank);
  }

  private static Row toRow(int row, CSVRecord record) {
    List<FieldErrorDTO> errors = new ArrayList<>();
    String year = value(record, "curricularYear");
    Integer curricularYear = null;
    if (!year.isBlank()) {
      try {
        curricularYear = Integer.valueOf(year);
      } catch (NumberFormatException e) {
        errors.add(new FieldErrorDTO("curricularYear", "INVALID", Map.of()));
      }
    }

    RegisterStudentRequestDTO student = new RegisterStudentRequestDTO(value(record, "name"),
        value(record, "personalEmail"), value(record, "degreeProgramme"), curricularYear);
    return new Row(row, student, errors);
  }

  // a short line just has nothing in its last columns
  private static String value(CSVRecord record, String column) {
    return record.isSet(column) ? record.get(column) : "";
  }

}
