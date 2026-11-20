package com.studymatch.dtos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

class ConfirmAttendanceRequestDTOTest {

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
  @DisplayName("Nobody attended is valid (boundary)")
  void emptyIsValid() {
    // Act
    Set<ConstraintViolation<ConfirmAttendanceRequestDTO>> violations = validator.validate(withAttendees(0));

    // Assert
    assertTrue(violations.isEmpty());
  }

  @Test
  @DisplayName("20 attendees, a full room, is valid (boundary)")
  void twentyIsValid() {
    // Act
    Set<ConstraintViolation<ConfirmAttendanceRequestDTO>> violations = validator.validate(withAttendees(20));

    // Assert
    assertTrue(violations.isEmpty());
  }

  @Test
  @DisplayName("21 attendees is more than a room holds (boundary)")
  void twentyOneIsInvalid() {
    // Act + Assert
    assertOnlyViolationOn(withAttendees(21));
  }

  @Test
  @DisplayName("The list is required")
  void missingListIsInvalid() {
    // Act + Assert
    assertOnlyViolationOn(new ConfirmAttendanceRequestDTO(null));
  }

  @Test
  @DisplayName("A student number can't be null")
  void nullNumberIsInvalid() {
    // Act + Assert
    assertOnlyViolationOn(new ConfirmAttendanceRequestDTO(new HashSet<>(Arrays.asList(1L, null))));
  }

  // every violation is on the list (a null element is reported as attendeeNumbers[].<iterable element>)
  private void assertOnlyViolationOn(ConfirmAttendanceRequestDTO request) {
    Set<ConstraintViolation<ConfirmAttendanceRequestDTO>> violations = validator.validate(request);

    assertEquals(1, violations.size());
    assertEquals(Set.of(true), violations.stream()
        .map(v -> v.getPropertyPath().toString().startsWith("attendeeNumbers"))
        .collect(Collectors.toSet()));
  }

  private static ConfirmAttendanceRequestDTO withAttendees(int count) {
    return new ConfirmAttendanceRequestDTO(LongStream.rangeClosed(1, count).boxed().collect(Collectors.toSet()));
  }

}
