package com.studymatch.models;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.studymatch.enums.RoomType;
import com.studymatch.exceptions.ErrorCode;
import com.studymatch.exceptions.StudyMatchException;

class WeeklySessionTest {

  @ParameterizedTest(name = "{0} minutes is accepted")
  @ValueSource(ints = { 60, 90, 120, 150, 180 })
  @DisplayName("Every length from 60 to 180 minutes in steps of 30 is accepted")
  void validLengths(int minutes) {
    // Act
    WeeklySession session = new WeeklySession(minutes, RoomType.LAB);

    // Assert
    assertEquals(minutes, session.getMinutes());
    assertSame(RoomType.LAB, session.getRoomType());
  }

  @ParameterizedTest(name = "{0} minutes is INVALID_SESSION_LENGTH")
  @ValueSource(ints = { 30, 59, 61, 100, 179, 181, 210, 0, -60 })
  @DisplayName("Below 60, above 180 or off the 30-minute slots is INVALID_SESSION_LENGTH (boundaries: 59, 61, 179, 181)")
  void invalidLengths(int minutes) {
    // Act
    StudyMatchException e = assertThrows(StudyMatchException.class, () -> new WeeklySession(minutes, RoomType.LAB));

    // Assert
    assertEquals(ErrorCode.INVALID_SESSION_LENGTH, e.getCode());
  }

}
