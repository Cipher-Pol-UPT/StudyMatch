package com.studymatch.models;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.DayOfWeek;
import java.time.LocalTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import com.studymatch.exceptions.ErrorCode;
import com.studymatch.exceptions.StudyMatchException;

class UnavailableSlotTest {

  private static UnavailableSlot slot(DayOfWeek day, String start) {
    return new UnavailableSlot(ModelTestData.teacher(), new AcademicYear(2026), day, LocalTime.parse(start));
  }

  @ParameterizedTest(name = "{0} at {1} fits the grid")
  @CsvSource({
      "MONDAY, 08:00", // first slot of the day (boundary)
      "MONDAY, 08:30",
      "WEDNESDAY, 13:00",
      "FRIDAY, 19:30" // last slot of the week, ends at 20:00 (boundary)
  })
  @DisplayName("A slot Monday to Friday, on the hour or half hour, from 8:00 to 19:30 is kept as given")
  void fitsGrid(DayOfWeek day, String start) {
    // Act
    UnavailableSlot slot = slot(day, start);

    // Assert
    assertEquals(day, slot.getDay());
    assertEquals(LocalTime.parse(start), slot.getStart());
    assertEquals(2026, slot.getAcademicYear().getStartYear());
  }

  @ParameterizedTest(name = "{0} isn't a teaching day")
  @EnumSource(value = DayOfWeek.class, names = { "SATURDAY", "SUNDAY" })
  @DisplayName("A slot on the weekend is INVALID_SLOT")
  void weekend(DayOfWeek day) {
    // Act
    StudyMatchException e = assertThrows(StudyMatchException.class, () -> slot(day, "10:00"));

    // Assert
    assertSame(ErrorCode.INVALID_SLOT, e.getCode());
  }

  @ParameterizedTest(name = "starting at {0}")
  @CsvSource({
      "07:30", // the slot before the first (boundary)
      "20:00", // would end at 20:30 (boundary)
      "23:30",
      "00:00",
      "08:15", // off the half hour
      "08:01",
      "08:29",
      "08:00:30" // seconds
  })
  @DisplayName("A slot outside 8:00 to 20:00 or off the half hour is INVALID_SLOT")
  void outsideGrid(String start) {
    // Act
    StudyMatchException e = assertThrows(StudyMatchException.class, () -> slot(DayOfWeek.TUESDAY, start));

    // Assert
    assertSame(ErrorCode.INVALID_SLOT, e.getCode());
  }

}
