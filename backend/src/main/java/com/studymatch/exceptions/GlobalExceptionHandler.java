package com.studymatch.exceptions;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.studymatch.dtos.ErrorResponseDTO;
import com.studymatch.dtos.FieldErrorDTO;

import tools.jackson.core.JacksonException;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;

@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(MaxUploadSizeExceededException.class)
  public ResponseEntity<ErrorResponseDTO> handleFileTooLarge(MaxUploadSizeExceededException e) {
    ErrorCode code = ErrorCode.FILE_TOO_LARGE;
    return ResponseEntity.status(code.getStatus()).body(ErrorResponseDTO.of(code, Map.of()));
  }

  // an upload request without the file in it
  @ExceptionHandler(MissingServletRequestPartException.class)
  public ResponseEntity<ErrorResponseDTO> handleMissingFile(MissingServletRequestPartException e) {
    FieldErrorDTO missing = new FieldErrorDTO(e.getRequestPartName(), "NOT_NULL", Map.of());
    return ResponseEntity.badRequest().body(ErrorResponseDTO.validationFailed(List.of(missing)));
  }

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

  // the body couldn't even be read: a wrong type (like an unknown enum value) names its field, broken JSON doesn't
  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ErrorResponseDTO> handleUnreadableBody(HttpMessageNotReadableException e) {
    List<FieldErrorDTO> fieldErrors = List.of();
    if (e.getCause() instanceof JacksonException jackson && !jackson.getPath().isEmpty()) {
      String field = jackson.getPath().getLast().getPropertyName();
      if (field != null) {
        fieldErrors = List.of(new FieldErrorDTO(field, "INVALID", Map.of()));
      }
    }
    return ResponseEntity.badRequest().body(ErrorResponseDTO.validationFailed(fieldErrors));
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
        .map(FieldErrorDTO::from)
        .toList();
    return ResponseEntity.badRequest().body(ErrorResponseDTO.validationFailed(fieldErrors));
  }

  private FieldErrorDTO toFieldError(FieldError error) {
    if (!error.contains(ConstraintViolation.class)) {
      return new FieldErrorDTO(error.getField(), "INVALID", Map.of());
    }
    return FieldErrorDTO.fromConstraint(error.getField(), error.unwrap(ConstraintViolation.class).getConstraintDescriptor());
  }

}
