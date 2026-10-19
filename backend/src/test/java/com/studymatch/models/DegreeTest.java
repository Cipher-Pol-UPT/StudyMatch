package com.studymatch.models;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import jakarta.validation.Validation;
import jakarta.validation.Validator;

class DegreeTest {

  private static Validator validator;

  @BeforeAll
  static void createValidator() {
    validator = Validation.buildDefaultValidatorFactory().getValidator();
  }

  private static Set<String> failures(Degree degree) {
    return validator.validate(degree).stream()
        .map(v -> v.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName())
        .collect(Collectors.toSet());
  }

  @ParameterizedTest(name = "year {0} exists in a 3-year degree")
  @ValueSource(ints = { 1, 2, 3 })
  @DisplayName("Every year from 1 to the degree's length exists (boundaries: 1 and 3)")
  void yearsInside(int year) {
    assertTrue(new Degree("Test Degree", 3).hasCurricularYear(year));
  }

  @ParameterizedTest(name = "year {0} doesn't exist in a 3-year degree")
  @ValueSource(ints = { 0, 4, -1 })
  @DisplayName("Year 0 and one past the end don't exist (boundaries: 0 and 4)")
  void yearsOutside(int year) {
    assertFalse(new Degree("Test Degree", 3).hasCurricularYear(year));
  }

  @ParameterizedTest(name = "{0} years is allowed")
  @ValueSource(ints = { 1, 6 })
  @DisplayName("A degree can have from 1 to 6 years (boundaries)")
  void lengthsAllowed(int years) {
    assertTrue(failures(new Degree("Test Degree", years)).isEmpty());
  }

  @Test
  @DisplayName("0 years or 7 years isn't a degree (boundaries)")
  void lengthsNotAllowed() {
    assertEquals(Set.of("Min"), failures(new Degree("Test Degree", 0)));
    assertEquals(Set.of("Max"), failures(new Degree("Test Degree", 7)));
  }

}
