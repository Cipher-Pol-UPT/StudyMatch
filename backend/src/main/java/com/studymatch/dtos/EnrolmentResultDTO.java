package com.studymatch.dtos;

import com.fasterxml.jackson.annotation.JsonInclude;

// what happened to each student; name only when the student exists, row only for the CSV import
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EnrolmentResultDTO(Integer row, Long number, String name, Result result) {

  public enum Result {
    ENROLLED,
    ALREADY_ENROLLED,
    ALREADY_PASSED,
    NOT_FOUND,
    INVALID_NUMBER,
    DUPLICATE_IN_FILE,
  }

  public static EnrolmentResultDTO of(Long number, String name, Result result) {
    return new EnrolmentResultDTO(null, number, name, result);
  }

  public EnrolmentResultDTO atRow(int row) {
    return new EnrolmentResultDTO(row, number, name, result);
  }

}
