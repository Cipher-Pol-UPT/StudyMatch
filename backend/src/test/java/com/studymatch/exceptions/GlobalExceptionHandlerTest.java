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
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

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
    Student student = new Student("s@example.com", "Test Student", "hash", new Degree("Test Degree"), 1);
    student.setAboutMe("a".repeat(500));

    // Act + Assert
    assertTrue(validator.validateProperty(student, "aboutMe").isEmpty());
  }

  @Test
  @DisplayName("About me with 501 characters gives SIZE_MAX with only the max")
  void aboutMeOverLimitIsSizeMax() {
    // Arrange
    Student student = new Student("s@example.com", "Test Student", "hash", new Degree("Test Degree"), 1);
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
