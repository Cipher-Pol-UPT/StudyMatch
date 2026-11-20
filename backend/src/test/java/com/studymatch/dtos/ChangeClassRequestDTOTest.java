package com.studymatch.dtos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

class ChangeClassRequestDTOTest {

  private static ValidatorFactory validatorFactory;
  private static Validator validator;

  @BeforeAll
  static void createValidator() {
    validatorFactory = Validation.buildDefaultValidatorFactory();
    validator = validatorFactory.getValidator();
  }

  @AfterAll
  static void closeValidator() {
    validatorFactory.close();
  }

  @Test
  @DisplayName("A class code is valid")
  void classIsValid() {
    // Act
    Set<ConstraintViolation<ChangeClassRequestDTO>> violations = validator.validate(new ChangeClassRequestDTO("T2"));

    // Assert
    assertTrue(violations.isEmpty());
  }

  @Test
  @DisplayName("The class is required")
  void blankClassIsInvalid() {
    // Act + Assert
    assertEquals(Set.of("NotBlank"), violatedAnnotations(new ChangeClassRequestDTO(" ")));
  }

  @Test
  @DisplayName("The class can't be missing")
  void missingClassIsInvalid() {
    // Act + Assert
    assertEquals(Set.of("NotBlank"), violatedAnnotations(new ChangeClassRequestDTO(null)));
  }

  @Test
  @DisplayName("The class code can have 20 characters (boundary)")
  void classAtTheLimitIsValid() {
    // Act
    Set<ConstraintViolation<ChangeClassRequestDTO>> violations =
        validator.validate(new ChangeClassRequestDTO("a".repeat(20)));

    // Assert
    assertTrue(violations.isEmpty());
  }

  @Test
  @DisplayName("The class code cannot have 21 characters (boundary)")
  void classOverTheLimitIsInvalid() {
    // Act + Assert
    assertEquals(Set.of("Size"), violatedAnnotations(new ChangeClassRequestDTO("a".repeat(21))));
  }

  private Set<String> violatedAnnotations(ChangeClassRequestDTO request) {
    return validator.validate(request).stream()
        .map(v -> v.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName())
        .collect(Collectors.toSet());
  }

}
