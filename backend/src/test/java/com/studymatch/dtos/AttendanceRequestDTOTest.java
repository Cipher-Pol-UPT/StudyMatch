package com.studymatch.dtos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.studymatch.dtos.AttendanceRequestDTO.AbsentStudent;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

class AttendanceRequestDTOTest {

  private static final LocalDateTime STARTS_AT = LocalDateTime.of(2026, 11, 10, 9, 0);

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
  @DisplayName("No absences is valid, everyone came (boundary)")
  void noAbsencesIsValid() {
    // Act
    Set<ConstraintViolation<AttendanceRequestDTO>> violations = validator.validate(withAbsences(0));

    // Assert
    assertTrue(violations.isEmpty());
  }

  // class section

  @Test
  @DisplayName("The class is required")
  void blankClassIsInvalid() {
    // Act + Assert
    assertOnlyViolation(new AttendanceRequestDTO(" ", STARTS_AT, List.of()), "classSection");
  }

  @Test
  @DisplayName("The class code can have 20 characters (boundary)")
  void classAtTheLimitIsValid() {
    // Act
    Set<ConstraintViolation<AttendanceRequestDTO>> violations =
        validator.validate(new AttendanceRequestDTO("a".repeat(20), STARTS_AT, List.of()));

    // Assert
    assertTrue(violations.isEmpty());
  }

  @Test
  @DisplayName("The class code cannot have 21 characters (boundary)")
  void classOverTheLimitIsInvalid() {
    // Act + Assert
    assertOnlyViolation(new AttendanceRequestDTO("a".repeat(21), STARTS_AT, List.of()), "classSection");
  }

  // lesson start

  @Test
  @DisplayName("The lesson start is required")
  void missingStartIsInvalid() {
    // Act + Assert
    assertOnlyViolation(new AttendanceRequestDTO("T1", null, List.of()), "startsAt");
  }

  // absences

  @Test
  @DisplayName("The absence list is required, even if empty")
  void missingAbsencesIsInvalid() {
    // Act + Assert
    assertOnlyViolation(new AttendanceRequestDTO("T1", STARTS_AT, null), "absences");
  }

  @Test
  @DisplayName("200 absences at once is valid (boundary)")
  void twoHundredAbsencesIsValid() {
    // Act
    Set<ConstraintViolation<AttendanceRequestDTO>> violations = validator.validate(withAbsences(200));

    // Assert
    assertTrue(violations.isEmpty());
  }

  @Test
  @DisplayName("201 absences at once is too many (boundary)")
  void twoHundredAndOneAbsencesIsInvalid() {
    // Act + Assert
    assertOnlyViolation(withAbsences(201), "absences");
  }

  @Test
  @DisplayName("An absent student needs a number")
  void absenceWithoutNumberIsInvalid() {
    // Act + Assert
    assertOnlyViolation(new AttendanceRequestDTO("T1", STARTS_AT, List.of(new AbsentStudent(null, false))),
        "absences[0].studentNumber");
  }

  private void assertOnlyViolation(AttendanceRequestDTO request, String field) {
    Set<ConstraintViolation<AttendanceRequestDTO>> violations = validator.validate(request);

    assertEquals(Set.of(field), violations.stream().map(v -> v.getPropertyPath().toString()).collect(Collectors.toSet()));
  }

  private static AttendanceRequestDTO withAbsences(int count) {
    return new AttendanceRequestDTO("T1", STARTS_AT, LongStream.rangeClosed(1, count)
        .mapToObj(number -> new AbsentStudent(number, false))
        .toList());
  }

}
