package com.studymatch.dtos;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;

// the rows the staff confirmed from the preview, each one is checked again before anything is created
public record ImportStudentsRequestDTO(@NotEmpty List<Row> rows) {

  public record Row(int row, RegisterStudentRequestDTO student) {
  }

}
