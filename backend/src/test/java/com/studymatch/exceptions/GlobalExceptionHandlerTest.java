package com.studymatch.exceptions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import com.studymatch.dtos.CreateAccountRequestDTO;
import com.studymatch.dtos.ErrorResponseDTO;
import com.studymatch.dtos.FieldErrorDTO;
import com.studymatch.dtos.LoginRequestDTO;
import com.studymatch.models.Degree;
import com.studymatch.models.Student;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;

class GlobalExceptionHandlerTest {

  private static Validator validator;

  private GlobalExceptionHandler handler;

  @BeforeAll
  static void createValidator() {
    // same validation engine spring uses, slow to build so only once
    validator = Validation.buildDefaultValidatorFactory().getValidator();
  }

  @BeforeEach
  void setUp() {
    handler = new GlobalExceptionHandler();
  }

  // business errors

  @ParameterizedTest(name = "{0}")
  @EnumSource(ErrorCode.class)
  @DisplayName("Every error code is sent with its own status")
  void everyCodeUsesItsStatus(ErrorCode code) {
    // Act
    ResponseEntity<ErrorResponseDTO> response = handler.handleStudyMatchException(new StudyMatchException(code));

    // Assert
    assertEquals(code.getStatus().value(), response.getStatusCode().value());
    assertEquals(code.name(), response.getBody().code());
  }

  @Test
  @DisplayName("Error parameters are sent to the frontend")
  void paramsArePassedThrough() {
    // Arrange
    StudyMatchException e = new StudyMatchException(ErrorCode.CAPACITY_BELOW_PARTICIPANTS, Map.of("participants", 2));

    // Act
    ResponseEntity<ErrorResponseDTO> response = handler.handleStudyMatchException(e);

    // Assert
    assertEquals(2, response.getBody().params().get("participants"));
  }

  // security + unknown urls

  @Test
  @DisplayName("A failed login is 401 INVALID_CREDENTIALS")
  void failedLogin() {
    // Act
    ResponseEntity<ErrorResponseDTO> response =
        handler.handleAuthenticationException(new BadCredentialsException("bad credentials"));

    // Assert
    assertEquals(401, response.getStatusCode().value());
    assertEquals("INVALID_CREDENTIALS", response.getBody().code());
  }

  @Test
  @DisplayName("An unknown URL is 404 NOT_FOUND in our format")
  void unknownEndpoint() {
    // Act
    ResponseEntity<ErrorResponseDTO> response =
        handler.handleUnknownEndpoint(new NoResourceFoundException(HttpMethod.GET, "/nope", "nope"));

    // Assert
    assertEquals(404, response.getStatusCode().value());
    assertEquals("NOT_FOUND", response.getBody().code());
  }

  // uploads

  @Test
  @DisplayName("A file over the upload limit is 413 FILE_TOO_LARGE")
  void fileTooLarge() {
    // Act - MockMvc doesn't enforce the limit, so the handler is called directly
    ResponseEntity<ErrorResponseDTO> response = handler.handleFileTooLarge(new MaxUploadSizeExceededException(1024 * 1024));

    // Assert
    assertEquals(413, response.getStatusCode().value());
    assertEquals("FILE_TOO_LARGE", response.getBody().code());
  }

  @Test
  @DisplayName("An upload without the file is a field error on that part")
  void missingFile() {
    // Act
    ResponseEntity<ErrorResponseDTO> response = handler.handleMissingFile(new MissingServletRequestPartException("file"));

    // Assert
    assertEquals(400, response.getStatusCode().value());
    assertEquals(List.of(new FieldErrorDTO("file", "NOT_NULL", Map.of())), response.getBody().fieldErrors());
  }

  // bodies that can't even be read

  // what Spring throws when Jackson fails on this body
  private static HttpMessageNotReadableException unreadable(String json) {
    try {
      new JsonMapper().readValue(json, CreateAccountRequestDTO.class);
    } catch (JacksonException e) {
      return new HttpMessageNotReadableException("unreadable", e, new MockHttpInputMessage(json.getBytes()));
    }
    throw new IllegalStateException("this JSON was supposed to fail");
  }

  @Test
  @DisplayName("An unknown enum value is a field error on that field")
  void unknownEnumValue() {
    // Act - PRINCIPAL isn't a Role
    ResponseEntity<ErrorResponseDTO> response = handler.handleUnreadableBody(unreadable("{\"type\":\"PRINCIPAL\"}"));

    // Assert
    assertEquals(400, response.getStatusCode().value());
    assertEquals("VALIDATION_FAILED", response.getBody().code());
    assertEquals(List.of(new FieldErrorDTO("type", "INVALID", Map.of())), response.getBody().fieldErrors());
  }

  @Test
  @DisplayName("Broken JSON is VALIDATION_FAILED in our format, with no field to point at")
  void brokenJson() {
    // Act
    ResponseEntity<ErrorResponseDTO> response = handler.handleUnreadableBody(unreadable("{\"type\": "));

    // Assert
    assertEquals(400, response.getStatusCode().value());
    assertEquals("VALIDATION_FAILED", response.getBody().code());
    assertTrue(response.getBody().fieldErrors().isEmpty());
  }

  // validation errors -> codes

  @Test
  @DisplayName("Validation errors become VALIDATION_FAILED with one code per field")
  void validationErrorsBecomeCodes() {
    // Arrange - bad email and blank password
    Set<ConstraintViolation<LoginRequestDTO>> violations = validator.validate(new LoginRequestDTO("not-an-email", ""));

    // Act
    ResponseEntity<ErrorResponseDTO> response = handler.handleConstraintViolation(new ConstraintViolationException(violations));

    // Assert
    assertEquals(400, response.getStatusCode().value());
    assertEquals("VALIDATION_FAILED", response.getBody().code());
    List<FieldErrorDTO> fieldErrors = response.getBody().fieldErrors();
    assertTrue(fieldErrors.stream().anyMatch(f -> f.field().equals("email") && f.code().equals("EMAIL")));
    assertTrue(fieldErrors.stream().anyMatch(f -> f.field().equals("password") && f.code().equals("NOT_BLANK")));
  }

  @Test
  @DisplayName("About me with exactly 500 characters is fine")
  void aboutMeAtLimitIsValid() {
    // Arrange
    Student student = new Student("s@example.com", "Test Student", "hash", new Degree("Test Degree", 3), 1);
    student.setAboutMe("a".repeat(500));

    // Act + Assert
    assertTrue(validator.validateProperty(student, "aboutMe").isEmpty());
  }

  @Test
  @DisplayName("About me with 501 characters gives SIZE_MAX with only the max")
  void aboutMeOverLimitIsSizeMax() {
    // Arrange
    Student student = new Student("s@example.com", "Test Student", "hash", new Degree("Test Degree", 3), 1);
    student.setAboutMe("a".repeat(501));
    Set<ConstraintViolation<Student>> violations = validator.validateProperty(student, "aboutMe");

    // Act
    ResponseEntity<ErrorResponseDTO> response = handler.handleConstraintViolation(new ConstraintViolationException(violations));

    // Assert - no min, so it's SIZE_MAX instead of SIZE
    FieldErrorDTO error = response.getBody().fieldErrors().get(0);
    assertEquals("aboutMe", error.field());
    assertEquals("SIZE_MAX", error.code());
    assertEquals(500, error.params().get("max"));
    assertFalse(error.params().containsKey("min"));
  }

}
