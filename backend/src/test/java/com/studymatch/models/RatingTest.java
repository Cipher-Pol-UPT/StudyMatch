package com.studymatch.models;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDateTime;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.studymatch.exceptions.ErrorCode;
import com.studymatch.exceptions.StudyMatchException;

class RatingTest {

  private TutorRole tutorRole;
  private StudyRoom room;
  private Participation anaAttended;
  private Participation brunoDidNotAttend;

  @BeforeEach
  void setUp() {
    tutorRole = ModelTestData.tutorRole(ModelTestData.student("Tutor Test"));
    LocalDateTime start = LocalDateTime.now().plusDays(1);
    room = new StudyRoom(tutorRole, start, start.plusHours(2), 2, "test room", "https://meet.google.com/test-link");

    Student ana = ModelTestData.student("Ana Test");
    Student bruno = ModelTestData.student("Bruno Test");
    room.join(ana);
    room.join(bruno);

    // the room happened and ended an hour ago, only ana showed up
    endRoomAt(LocalDateTime.now().minusHours(1));
    room.confirmAttendance(Set.of(ana));

    anaAttended = participationOf(ana);
    brunoDidNotAttend = participationOf(bruno);
  }

  private void endRoomAt(LocalDateTime endsAt) {
    ReflectionTestUtils.setField(room, "startsAt", endsAt.minusHours(2));
    ReflectionTestUtils.setField(room, "endsAt", endsAt);
  }

  private Participation participationOf(Student student) {
    return room.getParticipations().stream()
        .filter(p -> p.getStudent() == student)
        .findFirst()
        .orElseThrow();
  }

  @Test
  @DisplayName("A confirmed attendee can rate the tutor")
  void attendeeCanRate() {
    // Act
    Rating rating = new Rating(anaAttended, 5, "great session");

    // Assert
    assertEquals(5, rating.getStars());
    assertEquals("great session", rating.getComment());
    assertNotNull(rating.getRatedAt());
    assertSame(tutorRole, rating.getTutorRole());
  }

  @Test
  @DisplayName("Someone who didn't attend can't rate")
  void nonAttendeeCannotRate() {
    // Act
    StudyMatchException e = assertThrows(StudyMatchException.class, () -> new Rating(brunoDidNotAttend, 1, null));

    // Assert
    assertEquals(ErrorCode.ATTENDANCE_NOT_CONFIRMED, e.getCode());
  }

  @Test
  @DisplayName("The comment is optional")
  void commentIsOptional() {
    // Act
    Rating rating = new Rating(anaAttended, 4, null);

    // Assert
    assertNull(rating.getComment());
  }

  // 7-day window after the room ends (a minute either side so the test isn't flaky)

  @Test
  @DisplayName("Rating is still allowed just before the 7 days are up")
  void ratingJustInsideWindow() {
    // Arrange
    endRoomAt(LocalDateTime.now().minusDays(7).plusMinutes(1));

    // Act + Assert
    assertDoesNotThrow(() -> new Rating(anaAttended, 4, null));
  }

  @Test
  @DisplayName("Rating is closed just after the 7 days are up")
  void ratingJustOutsideWindow() {
    // Arrange
    endRoomAt(LocalDateTime.now().minusDays(7).minusMinutes(1));

    // Act
    StudyMatchException e = assertThrows(StudyMatchException.class, () -> new Rating(anaAttended, 4, null));

    // Assert
    assertEquals(ErrorCode.RATING_WINDOW_CLOSED, e.getCode());
  }

}
