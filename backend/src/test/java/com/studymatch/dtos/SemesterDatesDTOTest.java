package com.studymatch.dtos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.studymatch.models.AcademicYear;

import jakarta.validation.Validation;
import jakarta.validation.Validator;

class SemesterDatesDTOTest {

  private static Validator validator;

  @BeforeAll
  static void createValidator() {
    validator = Validation.buildDefaultValidatorFactory().getValidator();
  }

  // field -> annotation that failed, e.g. semester1Start=NotNull
  private Set<String> failures(Object request) {
    return validator.validate(request).stream()
        .map(v -> v.getPropertyPath() + "=" + v.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName())
        .collect(Collectors.toSet());
  }

  @Test
  @DisplayName("Four dates are accepted, how they fit together is up to the academic year")
  void fourDates() {
    LocalDate day = LocalDate.of(2026, 9, 15);
    assertTrue(failures(new SemesterDatesDTO(day, day, day, day)).isEmpty());
  }

  @Test
  @DisplayName("Every missing date is NOT_NULL")
  void missingDates() {
    assertEquals(Set.of("semester1Start=NotNull", "semester1End=NotNull", "semester2Start=NotNull",
        "semester2End=NotNull"), failures(new SemesterDatesDTO(null, null, null, null)));
  }

  @Test
  @DisplayName("Starting a year needs its dates, and the ones inside are checked too")
  void startNeedsDates() {
    assertEquals(Set.of("semesters=NotNull"), failures(new StartAcademicYearRequestDTO(2026, null)));
    assertEquals(Set.of("semesters.semester2End=NotNull"), failures(new StartAcademicYearRequestDTO(2026,
        new SemesterDatesDTO(LocalDate.of(2026, 9, 15), LocalDate.of(2027, 1, 31), LocalDate.of(2027, 2, 15), null))));
  }

  @Test
  @DisplayName("A year from before semester dates comes out with none")
  void yearWithoutDates() {
    assertNull(SemesterDatesDTO.from(new AcademicYear(2025)));
  }

}
