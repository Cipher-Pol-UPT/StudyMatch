package com.studymatch.dtos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

class GradeRequestDTOTest {

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

  // which constraints failed, by annotation name
  private static Set<String> failed(GradeRequestDTO request) {
    return validator.validate(request).stream()
        .map(violation -> violation.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName())
        .collect(Collectors.toSet());
  }

  @ParameterizedTest
  @ValueSource(strings = { "0", "20", "14.5", "14.55", "9.99" })
  @DisplayName("Grades from 0 to 20 with up to 2 decimals are valid")
  void validGrades(String value) {
    // Act
    Set<ConstraintViolation<GradeRequestDTO>> violations = validator.validate(new GradeRequestDTO(new BigDecimal(value)));

    // Assert
    assertTrue(violations.isEmpty());
  }

  @ParameterizedTest
  @CsvSource({ "-0.01, DecimalMin", "-1, DecimalMin", "20.01, DecimalMax", "21, DecimalMax", "14.555, Digits" })
  @DisplayName("Below 0, above 20 or more than 2 decimals are refused")
  void invalidGrades(String value, String constraint) {
    // Act + Assert
    assertEquals(Set.of(constraint), failed(new GradeRequestDTO(new BigDecimal(value))));
  }

  @Test
  @DisplayName("A missing grade is refused")
  void missingGrade() {
    // Act + Assert
    assertEquals(Set.of("NotNull"), failed(new GradeRequestDTO(null)));
  }

}
