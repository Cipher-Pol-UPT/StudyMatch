package com.studymatch.dtos;

import java.util.List;

// what happened to each confirmed row; number and institutionalEmail are only there when the account exists
public record ImportResultRowDTO(int row, Status status, Long number, String institutionalEmail,
    List<FieldErrorDTO> errors) {

  public enum Status {
    CREATED,
    REJECTED,
    EMAIL_NOT_SENT,
  }

  public static ImportResultRowDTO rejected(int row, List<FieldErrorDTO> errors) {
    return new ImportResultRowDTO(row, Status.REJECTED, null, null, errors);
  }

  public static ImportResultRowDTO created(int row, RegisteredStudentDTO student) {
    Status status = student.emailSent() ? Status.CREATED : Status.EMAIL_NOT_SENT;
    return new ImportResultRowDTO(row, status, student.number(), student.institutionalEmail(), List.of());
  }

}
