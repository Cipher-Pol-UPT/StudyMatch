package com.studymatch.models;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AcademicYearTest {

  @Test
  @DisplayName("1 September is the first day of that year's academic year")
  void firstOfSeptemberStartsTheYear() {
    // Act + Assert
    assertEquals(2026, AcademicYear.startYearOf(LocalDate.of(2026, 9, 1)));
  }

  @Test
  @DisplayName("31 August still belongs to the academic year that started the year before")
  void lastOfAugustIsPreviousYear() {
    // Act + Assert
    assertEquals(2025, AcademicYear.startYearOf(LocalDate.of(2026, 8, 31)));
  }

  @Test
  @DisplayName("1 January belongs to the academic year that started the September before")
  void firstOfJanuaryIsPreviousYear() {
    // Act + Assert
    assertEquals(2025, AcademicYear.startYearOf(LocalDate.of(2026, 1, 1)));
  }

  @Test
  @DisplayName("31 December belongs to the academic year that started that September")
  void lastOfDecemberIsThisYear() {
    // Act + Assert
    assertEquals(2026, AcademicYear.startYearOf(LocalDate.of(2026, 12, 31)));
  }

  @Test
  @DisplayName("The next year starts one calendar year later")
  void nextIsOneYearLater() {
    // Arrange
    AcademicYear year2026 = new AcademicYear(2026);

    // Act
    AcademicYear next = year2026.next();

    // Assert
    assertEquals(2027, next.getStartYear());
    assertEquals(2026, year2026.getStartYear());
  }

}
