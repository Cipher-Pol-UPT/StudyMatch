package com.studymatch.dtos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

class RateTutorRequestDTOTest {

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

  // stars

  @ParameterizedTest(name = "{0} stars is valid")
  @ValueSource(ints = { 1, 5 })
  @DisplayName("1 and 5 stars are valid (boundaries)")
  void starsAtTheLimitsAreValid(int stars) {
    // Act
    Set<ConstraintViolation<RateTutorRequestDTO>> violations = validator.validate(new RateTutorRequestDTO(stars, null));

    // Assert
    assertTrue(violations.isEmpty());
  }

  @ParameterizedTest(name = "{0} stars is invalid")
  @ValueSource(ints = { 0, 6 })
  @DisplayName("0 and 6 stars are out of range (boundaries)")
  void starsOutOfRangeAreInvalid(int stars) {
    // Act + Assert
    assertOnlyViolation(new RateTutorRequestDTO(stars, null), "stars");
  }

  @Test
  @DisplayName("The stars are required")
  void missingStarsIsInvalid() {
    // Act + Assert
    assertOnlyViolation(new RateTutorRequestDTO(null, "good"), "stars");
  }

  // comment

  @Test
  @DisplayName("The comment can have 500 characters (boundary)")
  void commentAtTheLimitIsValid() {
    // Act
    Set<ConstraintViolation<RateTutorRequestDTO>> violations =
        validator.validate(new RateTutorRequestDTO(4, "a".repeat(500)));

    // Assert
    assertTrue(violations.isEmpty());
  }

  @Test
  @DisplayName("The comment cannot have 501 characters (boundary)")
  void commentOverTheLimitIsInvalid() {
    // Act + Assert
    assertOnlyViolation(new RateTutorRequestDTO(4, "a".repeat(501)), "comment");
  }

  private void assertOnlyViolation(RateTutorRequestDTO request, String field) {
    Set<ConstraintViolation<RateTutorRequestDTO>> violations = validator.validate(request);

    assertEquals(Set.of(field), violations.stream().map(v -> v.getPropertyPath().toString()).collect(Collectors.toSet()));
  }

}
