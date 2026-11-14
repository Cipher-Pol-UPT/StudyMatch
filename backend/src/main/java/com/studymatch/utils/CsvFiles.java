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

import com.studymatch.exceptions.ErrorCode;
import com.studymatch.exceptions.StudyMatchException;

// the reading every CSV import shares, what a line means is up to each reader
public final class CsvFiles {

  public static final int MAX_ROWS = 200;

  // the invisible mark Excel puts at the start of UTF-8 files
  private static final String BOM = "\uFEFF";

  // row is the line in the spreadsheet (the header is row 1)
  public record Line(int row, CSVRecord record) {
  }

  private CsvFiles() {
  }

  // starts with the BOM so Excel opens (and saves) it as UTF-8
  public static String template(List<String> columns) {
    return BOM + String.join(",", columns) + "\r\n";
  }

  public static List<Line> read(byte[] file, List<String> columns) {
    String text = decodeUtf8(file);
    if (text.startsWith(BOM)) {
      text = text.substring(1); // otherwise the first column would be read as BOM + its name
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
        .setIgnoreEmptyLines(false) // still skipped below, but counted so the row numbers match the spreadsheet
        .get();

    List<Line> lines = new ArrayList<>();
    try (CSVParser parser = CSVParser.parse(new StringReader(text), format)) {
      checkColumns(parser.getHeaderNames(), columns);

      int row = 1;
      for (CSVRecord record : parser) {
        row++;
        if (isBlank(record)) {
          continue; // Excel likes to leave empty ",,," lines at the end
        }
        if (lines.size() == MAX_ROWS) {
          throw new StudyMatchException(ErrorCode.CSV_TOO_MANY_ROWS, Map.of("max", MAX_ROWS));
        }
        lines.add(new Line(row, record));
      }
    } catch (IOException | UncheckedIOException | IllegalArgumentException | IllegalStateException e) {
      // unclosed quotes, repeated column names...
      throw new StudyMatchException(ErrorCode.CSV_INVALID);
    }

    if (lines.isEmpty()) {
      throw new StudyMatchException(ErrorCode.CSV_EMPTY);
    }
    return lines;
  }

  // a short line just has nothing in its last columns
  public static String value(CSVRecord record, String column) {
    return record.isSet(column) ? record.get(column) : "";
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

  private static void checkColumns(List<String> headerNames, List<String> columns) {
    List<String> missing = columns.stream()
        .filter(column -> headerNames.stream().noneMatch(column::equalsIgnoreCase))
        .toList();
    if (!missing.isEmpty()) {
      throw new StudyMatchException(ErrorCode.CSV_MISSING_COLUMNS, Map.of("columns", String.join(", ", missing)));
    }
  }

  private static boolean isBlank(CSVRecord record) {
    return record.stream().allMatch(String::isBlank);
  }

}
