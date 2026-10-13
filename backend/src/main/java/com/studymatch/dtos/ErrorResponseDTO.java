package com.studymatch.dtos;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.studymatch.exceptions.ErrorCode;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ErrorResponseDTO(String code, Map<String, Object> params, List<FieldErrorDTO> fieldErrors) {

  public static ErrorResponseDTO of(ErrorCode code, Map<String, Object> params) {
    return new ErrorResponseDTO(code.name(), params, List.of());
  }

  public static ErrorResponseDTO validationFailed(List<FieldErrorDTO> fieldErrors) {
    return new ErrorResponseDTO(ErrorCode.VALIDATION_FAILED.name(), Map.of(), fieldErrors);
  }

}
