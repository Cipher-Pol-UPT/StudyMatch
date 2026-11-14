package com.studymatch.dtos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
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

class EnrolStudentsRequestDTOTest {

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
  @DisplayName("A class and one student number is valid")
  void oneStudentIsValid() {
    // Act
    Set<ConstraintViolation<EnrolStudentsRequestDTO>> violations = validator.validate(withStudents(1));

    // Assert
    assertTrue(violations.isEmpty());
  }

  // class section

  @Test
  @DisplayName("The class is required")
  void blankClassIsInvalid() {
    // Act + Assert
    assertOnlyViolation(new EnrolStudentsRequestDTO(" ", List.of(1L)), "classSection");
  }

  @Test
  @DisplayName("The class code can have 20 characters (boundary)")
  void classAtTheLimitIsValid() {
    // Act
    Set<ConstraintViolation<EnrolStudentsRequestDTO>> violations =
        validator.validate(new EnrolStudentsRequestDTO("a".repeat(20), List.of(1L)));

    // Assert
    assertTrue(violations.isEmpty());
  }

  @Test
  @DisplayName("The class code cannot have 21 characters (boundary)")
  void classOverTheLimitIsInvalid() {
    // Act + Assert
    assertOnlyViolation(new EnrolStudentsRequestDTO("a".repeat(21), List.of(1L)), "classSection");
  }

  // student numbers

  @Test
  @DisplayName("At least one student is needed (boundary)")
  void noStudentsIsInvalid() {
    // Act + Assert
    assertOnlyViolation(withStudents(0), "studentNumbers");
  }

  @Test
  @DisplayName("200 students at once is valid (boundary)")
  void twoHundredStudentsIsValid() {
    // Act
    Set<ConstraintViolation<EnrolStudentsRequestDTO>> violations = validator.validate(withStudents(200));

    // Assert
    assertTrue(violations.isEmpty());
  }

  @Test
  @DisplayName("201 students at once is too many (boundary)")
  void twoHundredAndOneStudentsIsInvalid() {
    // Act + Assert
    assertOnlyViolation(withStudents(201), "studentNumbers");
  }

  @Test
  @DisplayName("A student number can't be null")
  void nullNumberIsInvalid() {
    // Act + Assert
    assertOnlyViolation(new EnrolStudentsRequestDTO("T1", Arrays.asList(1L, null)), "studentNumbers[1].<list element>");
  }

  private void assertOnlyViolation(EnrolStudentsRequestDTO request, String field) {
    Set<ConstraintViolation<EnrolStudentsRequestDTO>> violations = validator.validate(request);

    assertEquals(Set.of(field), violations.stream().map(v -> v.getPropertyPath().toString()).collect(Collectors.toSet()));
  }

  private static EnrolStudentsRequestDTO withStudents(int count) {
    return new EnrolStudentsRequestDTO("T1", LongStream.rangeClosed(1, count).boxed().toList());
  }

}
