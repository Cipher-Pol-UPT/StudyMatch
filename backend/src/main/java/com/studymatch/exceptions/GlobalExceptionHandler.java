package com.studymatch.exceptions;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.studymatch.dtos.ErrorResponseDTO;
import com.studymatch.dtos.FieldErrorDTO;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.metadata.ConstraintDescriptor;

@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Set<String> IGNORED_ATTRIBUTES = Set.of("message", "groups", "payload", "regexp", "flags");

  @ExceptionHandler(StudyMatchException.class)
  public ResponseEntity<ErrorResponseDTO> handleStudyMatchException(StudyMatchException e) {
    return ResponseEntity.status(e.getCode().getStatus()).body(ErrorResponseDTO.of(e.getCode(), e.getParams()));
  }

  @ExceptionHandler(AuthenticationException.class)
  public ResponseEntity<ErrorResponseDTO> handleAuthenticationException(AuthenticationException e) {
    ErrorCode code = ErrorCode.INVALID_CREDENTIALS;
    return ResponseEntity.status(code.getStatus()).body(ErrorResponseDTO.of(code, Map.of()));
  }

  @ExceptionHandler(NoResourceFoundException.class)
  public ResponseEntity<ErrorResponseDTO> handleUnknownEndpoint(NoResourceFoundException e) {
    ErrorCode code = ErrorCode.NOT_FOUND;
    return ResponseEntity.status(code.getStatus()).body(ErrorResponseDTO.of(code, Map.of()));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErrorResponseDTO> handleInvalidRequest(MethodArgumentNotValidException e) {
    List<FieldErrorDTO> fieldErrors = e.getBindingResult().getFieldErrors().stream()
        .map(this::toFieldError)
        .toList();
    return ResponseEntity.badRequest().body(ErrorResponseDTO.validationFailed(fieldErrors));
  }

  @ExceptionHandler(ConstraintViolationException.class)
  public ResponseEntity<ErrorResponseDTO> handleConstraintViolation(ConstraintViolationException e) {
    List<FieldErrorDTO> fieldErrors = e.getConstraintViolations().stream()
        .map(v -> toFieldError(v.getPropertyPath().toString(), v.getConstraintDescriptor()))
        .toList();
    return ResponseEntity.badRequest().body(ErrorResponseDTO.validationFailed(fieldErrors));
  }

  private FieldErrorDTO toFieldError(FieldError error) {
    if (!error.contains(ConstraintViolation.class)) {
      return new FieldErrorDTO(error.getField(), "INVALID", Map.of());
    }
    return toFieldError(error.getField(), error.unwrap(ConstraintViolation.class).getConstraintDescriptor());
  }

  private FieldErrorDTO toFieldError(String field, ConstraintDescriptor<?> descriptor) {
    String code = toUpperSnakeCase(descriptor.getAnnotation().annotationType().getSimpleName());
    Map<String, Object> params = new LinkedHashMap<>(descriptor.getAttributes());
    params.keySet().removeAll(IGNORED_ATTRIBUTES);
    if (code.equals("SIZE") && Integer.valueOf(0).equals(params.get("min"))) {
      code = "SIZE_MAX";
      params.remove("min");
    }
    return new FieldErrorDTO(field, code, params);
  }

  private static String toUpperSnakeCase(String name) {
    return name.replaceAll("([a-z])([A-Z])", "$1_$2").toUpperCase();
  }

}
