package com.studymatch.utils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.stereotype.Component;

// export groups: one line per student, the same columns Import Groups reads
@Component
public class GroupCsvWriter {

  public static final List<String> COLUMNS = List.of("groupNumber", "studentNumber", "name", "institutionalEmail");

  // only what the file shows, never the statute (GR7)
  public record Row(int groupNumber, Long studentNumber, String name, String institutionalEmail) {
  }

  // after the template's BOM and header, so Excel opens it as UTF-8 like the import templates
  public byte[] write(List<Row> rows) {
    StringBuilder out = new StringBuilder(CsvFiles.template(COLUMNS));
    try (CSVPrinter printer = new CSVPrinter(out, CSVFormat.DEFAULT)) {
      for (Row row : rows) {
        printer.printRecord(row.groupNumber(), row.studentNumber(), row.name(),
            row.institutionalEmail() == null ? "" : row.institutionalEmail());
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return out.toString().getBytes(StandardCharsets.UTF_8);
  }

}
