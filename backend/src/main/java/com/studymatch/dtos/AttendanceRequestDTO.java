package com.studymatch.dtos;

import java.time.LocalDateTime;
import java.util.List;

import com.studymatch.utils.CsvFiles;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// the lesson (class and start time) and who missed it; anyone not listed was present, so an empty list is fine
public record AttendanceRequestDTO(
    @NotBlank @Size(max = 20) String classSection,
    @NotNull LocalDateTime startsAt,
    @NotNull @Size(max = MAX_STUDENTS) List<@NotNull @Valid AbsentStudent> absences) {

  public static final int MAX_STUDENTS = CsvFiles.MAX_ROWS;

  public record AbsentStudent(@NotNull Long studentNumber, boolean justified) {
  }

}
