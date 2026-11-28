package com.studymatch.models;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.studymatch.exceptions.ErrorCode;
import com.studymatch.exceptions.StudyMatchException;

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

  // semester dates, all on 2026/2027 (1 September 2026 to 31 August 2027)

  private static LocalDate date(String iso) {
    return LocalDate.parse(iso);
  }

  private static StudyMatchException refusal(String s1Start, String s1End, String s2Start, String s2End) {
    AcademicYear year = new AcademicYear(2026);
    StudyMatchException e = assertThrows(StudyMatchException.class,
        () -> year.setSemesters(date(s1Start), date(s1End), date(s2Start), date(s2End)));
    assertFalse(year.hasSemesters());
    return e;
  }

  @Test
  @DisplayName("A year runs from 1 September to 31 August")
  void firstAndLastDay() {
    assertEquals(date("2026-09-01"), new AcademicYear(2026).firstDay());
    assertEquals(date("2027-08-31"), new AcademicYear(2026).lastDay());
  }

  @Test
  @DisplayName("A new year has no semester dates until they're set")
  void noSemestersYet() {
    assertFalse(new AcademicYear(2026).hasSemesters());
  }

  @Test
  @DisplayName("Semesters from the first to the last day of the year, a day apart, are kept (boundaries)")
  void tightestSemesters() {
    // Arrange
    AcademicYear year = new AcademicYear(2026);

    // Act
    year.setSemesters(date("2026-09-01"), date("2026-09-02"), date("2026-09-03"), date("2027-08-31"));

    // Assert
    assertTrue(year.hasSemesters());
    assertEquals(date("2026-09-01"), year.getSemester1Start());
    assertEquals(date("2026-09-02"), year.getSemester1End());
    assertEquals(date("2026-09-03"), year.getSemester2Start());
    assertEquals(date("2027-08-31"), year.getSemester2End());
  }

  @ParameterizedTest(name = "{0} to {1}, {2} to {3}")
  @CsvSource({
      "2026-08-31, 2027-01-31, 2027-02-15, 2027-06-30", // the day before the year starts
      "2026-09-15, 2027-01-31, 2027-02-15, 2027-09-01", // the day after the year ends
      "2026-09-15, 2027-01-31, 2027-02-15, 2028-06-30" // a year late
  })
  @DisplayName("A date outside 1 September to 31 August is SEMESTER_OUTSIDE_YEAR with the limits (boundaries)")
  void outsideYear(String s1Start, String s1End, String s2Start, String s2End) {
    // Act
    StudyMatchException e = refusal(s1Start, s1End, s2Start, s2End);

    // Assert
    assertEquals(ErrorCode.SEMESTER_OUTSIDE_YEAR, e.getCode());
    assertEquals(Map.of("from", "2026-09-01", "to", "2027-08-31"), e.getParams());
  }

  @ParameterizedTest(name = "semester {4}: {0} to {1}, {2} to {3}")
  @CsvSource({
      "2026-09-15, 2026-09-15, 2027-02-15, 2027-06-30, 1", // ends the day it starts
      "2026-09-15, 2026-09-14, 2027-02-15, 2027-06-30, 1", // ends before it starts
      "2026-09-15, 2027-01-31, 2027-06-30, 2027-06-30, 2",
      "2026-09-15, 2027-01-31, 2027-06-30, 2027-02-15, 2"
  })
  @DisplayName("A semester ending on or before its start is SEMESTER_ENDS_BEFORE_START naming it (boundary)")
  void endsBeforeStart(String s1Start, String s1End, String s2Start, String s2End, int semester) {
    // Act
    StudyMatchException e = refusal(s1Start, s1End, s2Start, s2End);

    // Assert
    assertEquals(ErrorCode.SEMESTER_ENDS_BEFORE_START, e.getCode());
    assertEquals(Map.of("semester", semester), e.getParams());
  }

  @ParameterizedTest(name = "semester 2 starts {0}")
  @ValueSource(strings = { "2027-01-31", "2027-01-15" })
  @DisplayName("Semester 2 starting on or before the day semester 1 ends is SEMESTERS_OVERLAP (boundary)")
  void semestersOverlap(String s2Start) {
    // Act
    StudyMatchException e = refusal("2026-09-15", "2027-01-31", s2Start, "2027-06-30");

    // Assert
    assertEquals(ErrorCode.SEMESTERS_OVERLAP, e.getCode());
  }

  @Test
  @DisplayName("Dates with several problems get the first rule they break: outside the year comes first")
  void ruleOrder() {
    // Act - outside the year, semester 1 backwards and overlapping all at once
    StudyMatchException e = refusal("2026-08-01", "2026-07-01", "2026-06-01", "2027-06-30");

    // Assert
    assertEquals(ErrorCode.SEMESTER_OUTSIDE_YEAR, e.getCode());
  }

  @Test
  @DisplayName("Setting new dates replaces the old ones")
  void replaceDates() {
    // Arrange
    AcademicYear year = new AcademicYear(2026);
    year.setSemesters(date("2026-09-15"), date("2027-01-31"), date("2027-02-15"), date("2027-06-30"));

    // Act
    year.setSemesters(date("2026-09-22"), date("2027-02-07"), date("2027-02-22"), date("2027-07-07"));

    // Assert
    assertEquals(date("2026-09-22"), year.getSemester1Start());
    assertEquals(date("2027-07-07"), year.getSemester2End());
  }

  @ParameterizedTest
  @CsvSource({ "2026-09-15, 1", "2027-01-31, 1", "2027-02-10, 0", "2027-02-15, 2", "2027-06-30, 2", "2027-07-01, 0",
      "2026-09-14, 0" })
  @DisplayName("A date is in semester 1, semester 2, or neither, both ends included")
  void semesterOf(LocalDate date, int semester) {
    // Arrange
    AcademicYear year = new AcademicYear(2026);
    year.setSemesters(LocalDate.of(2026, 9, 15), LocalDate.of(2027, 1, 31), LocalDate.of(2027, 2, 15),
        LocalDate.of(2027, 6, 30));

    // Act + Assert
    assertEquals(semester, year.semesterOf(date));
  }

  @Test
  @DisplayName("A year without dates has no date in a semester")
  void semesterOfWithoutDates() {
    // Arrange
    AcademicYear year = new AcademicYear(2026);

    // Act + Assert
    assertEquals(0, year.semesterOf(LocalDate.of(2026, 10, 1)));
  }

}
