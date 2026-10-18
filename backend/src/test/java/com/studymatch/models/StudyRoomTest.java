package com.studymatch.models;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.studymatch.enums.StudyRoomStatus;
import com.studymatch.exceptions.ErrorCode;
import com.studymatch.exceptions.StudyMatchException;

class StudyRoomTest {

  private static final String LINK = "https://meet.google.com/test-link";

  private Student tutor;
  private TutorRole tutorRole;
  private StudyRoom room;
  private Student ana;
  private Student bruno;
  private Student carla;

  @BeforeEach
  void setUp() {
    tutor = ModelTestData.student("Tutor Test");
    tutorRole = ModelTestData.tutorRole(tutor);
    LocalDateTime start = LocalDateTime.now().plusDays(1);
    room = new StudyRoom(tutorRole, start, start.plusHours(2), 2, "test room", LINK);
    ana = ModelTestData.student("Ana Test");
    bruno = ModelTestData.student("Bruno Test");
    carla = ModelTestData.student("Carla Test");
  }

  // the constructor won't accept past dates, so to simulate time passing we move the room afterwards
  private void moveRoomTo(LocalDateTime startsAt, LocalDateTime endsAt) {
    ReflectionTestUtils.setField(room, "startsAt", startsAt);
    ReflectionTestUtils.setField(room, "endsAt", endsAt);
  }

  private void moveRoomToThePast() {
    moveRoomTo(LocalDateTime.now().minusHours(3), LocalDateTime.now().minusHours(1));
  }

  private Participation participationOf(Student student) {
    return room.getParticipations().stream()
        .filter(p -> p.getStudent() == student)
        .findFirst()
        .orElseThrow();
  }

  // create (#49)

  @Test
  @DisplayName("A new room is scheduled with no participants")
  void newRoomIsScheduled() {
    assertEquals(StudyRoomStatus.SCHEDULED, room.getStatus());
    assertTrue(room.getParticipations().isEmpty());
  }

  @Test
  @DisplayName("A room can't start in the past")
  void startInThePastIsRejected() {
    // Arrange - one minute ago
    LocalDateTime start = LocalDateTime.now().minusMinutes(1);

    // Act
    StudyMatchException e = assertThrows(StudyMatchException.class,
        () -> new StudyRoom(tutorRole, start, start.plusHours(1), 2, null, LINK));

    // Assert
    assertEquals(ErrorCode.STUDYROOM_IN_PAST, e.getCode());
  }

  @Test
  @DisplayName("A room can't end at the same time it starts")
  void endEqualToStartIsRejected() {
    // Arrange
    LocalDateTime start = LocalDateTime.now().plusDays(1);

    // Act - end == start, right at the limit
    StudyMatchException e = assertThrows(StudyMatchException.class,
        () -> new StudyRoom(tutorRole, start, start, 2, null, LINK));

    // Assert
    assertEquals(ErrorCode.STUDYROOM_ENDS_BEFORE_START, e.getCode());
  }

  @Test
  @DisplayName("A room ending one minute after it starts is fine")
  void endJustAfterStartIsAccepted() {
    LocalDateTime start = LocalDateTime.now().plusDays(1);
    assertDoesNotThrow(() -> new StudyRoom(tutorRole, start, start.plusMinutes(1), 2, null, LINK));
  }

  // join (#45)

  @Test
  @DisplayName("Joining adds the student as a participant")
  void joinAddsParticipant() {
    // Act
    room.join(ana);

    // Assert
    assertEquals(1, room.getParticipations().size());
    assertEquals(ana, room.getParticipations().iterator().next().getStudent());
  }

  @Test
  @DisplayName("Capacity 2: scheduled with 1, full with 2, a 3rd can't join")
  void capacityBoundaries() {
    // below capacity
    room.join(ana);
    assertEquals(StudyRoomStatus.SCHEDULED, room.getStatus());

    // at capacity
    room.join(bruno);
    assertEquals(StudyRoomStatus.FULL, room.getStatus());

    // above capacity
    StudyMatchException e = assertThrows(StudyMatchException.class, () -> room.join(carla));
    assertEquals(ErrorCode.STUDYROOM_FULL, e.getCode());
  }

  @Test
  @DisplayName("The tutor can't join their own room")
  void tutorCannotJoinOwnRoom() {
    StudyMatchException e = assertThrows(StudyMatchException.class, () -> room.join(tutor));
    assertEquals(ErrorCode.TUTOR_CANNOT_JOIN_OWN_STUDYROOM, e.getCode());
  }

  @Test
  @DisplayName("A student can't join the same room twice")
  void joinTwiceIsRejected() {
    // Arrange
    room.join(ana);

    // Act
    StudyMatchException e = assertThrows(StudyMatchException.class, () -> room.join(ana));

    // Assert
    assertEquals(ErrorCode.ALREADY_JOINED_STUDYROOM, e.getCode());
  }

  @Test
  @DisplayName("A room starting in a minute can still be joined")
  void joinJustBeforeStart() {
    // Arrange
    moveRoomTo(LocalDateTime.now().plusMinutes(1), LocalDateTime.now().plusHours(1));

    // Act + Assert
    assertDoesNotThrow(() -> room.join(ana));
  }

  @Test
  @DisplayName("A room that already started can't be joined")
  void joinAfterStartIsRejected() {
    // Arrange - started a minute ago
    moveRoomTo(LocalDateTime.now().minusMinutes(1), LocalDateTime.now().plusHours(1));

    // Act
    StudyMatchException e = assertThrows(StudyMatchException.class, () -> room.join(ana));

    // Assert
    assertEquals(ErrorCode.STUDYROOM_ALREADY_STARTED, e.getCode());
  }

  @Test
  @DisplayName("A cancelled room can't be joined")
  void joinCancelledRoomIsRejected() {
    // Arrange
    room.cancel(null);

    // Act
    StudyMatchException e = assertThrows(StudyMatchException.class, () -> room.join(ana));

    // Assert
    assertEquals(ErrorCode.STUDYROOM_CLOSED, e.getCode());
  }

  // leave (#47)

  @Test
  @DisplayName("Leaving a full room makes it scheduled again")
  void leavingFullRoomFreesAPlace() {
    // Arrange
    room.join(ana);
    room.join(bruno);

    // Act
    room.leave(ana);

    // Assert
    assertEquals(1, room.getParticipations().size());
    assertEquals(StudyRoomStatus.SCHEDULED, room.getStatus());
  }

  @Test
  @DisplayName("Only participants can leave")
  void leaveWithoutJoiningIsRejected() {
    StudyMatchException e = assertThrows(StudyMatchException.class, () -> room.leave(ana));
    assertEquals(ErrorCode.NOT_A_PARTICIPANT, e.getCode());
  }

  @Test
  @DisplayName("A student can't leave after the room started")
  void leaveAfterStartIsRejected() {
    // Arrange
    room.join(ana);
    moveRoomTo(LocalDateTime.now().minusMinutes(1), LocalDateTime.now().plusHours(1));

    // Act
    StudyMatchException e = assertThrows(StudyMatchException.class, () -> room.leave(ana));

    // Assert
    assertEquals(ErrorCode.STUDYROOM_ALREADY_STARTED, e.getCode());
  }

  // edit (#51)

  @Test
  @DisplayName("Raising the capacity of a full room makes it scheduled again")
  void raisingCapacityOfFullRoom() {
    // Arrange
    room.join(ana);
    room.join(bruno);

    // Act
    room.update(room.getStartsAt(), room.getEndsAt(), 3, "test room", LINK);

    // Assert
    assertEquals(StudyRoomStatus.SCHEDULED, room.getStatus());
  }

  @Test
  @DisplayName("Capacity can go down to exactly the number of participants")
  void capacityEqualToParticipantsIsAccepted() {
    // Arrange - capacity 3 with 2 people
    room.update(room.getStartsAt(), room.getEndsAt(), 3, "test room", LINK);
    room.join(ana);
    room.join(bruno);

    // Act + Assert - back to 2, right at the limit
    assertDoesNotThrow(() -> room.update(room.getStartsAt(), room.getEndsAt(), 2, "test room", LINK));
  }

  @Test
  @DisplayName("Capacity can't go below the number of participants")
  void capacityBelowParticipantsIsRejected() {
    // Arrange
    room.join(ana);
    room.join(bruno);

    // Act
    StudyMatchException e = assertThrows(StudyMatchException.class,
        () -> room.update(room.getStartsAt(), room.getEndsAt(), 1, "test room", LINK));

    // Assert - and the frontend gets the current count
    assertEquals(ErrorCode.CAPACITY_BELOW_PARTICIPANTS, e.getCode());
    assertEquals(2, e.getParams().get("participants"));
  }

  @Test
  @DisplayName("A room can't be edited after it started")
  void updateAfterStartIsRejected() {
    // Arrange
    moveRoomTo(LocalDateTime.now().minusMinutes(1), LocalDateTime.now().plusHours(1));
    LocalDateTime newStart = LocalDateTime.now().plusDays(2);

    // Act
    StudyMatchException e = assertThrows(StudyMatchException.class,
        () -> room.update(newStart, newStart.plusHours(1), 2, "test room", LINK));

    // Assert
    assertEquals(ErrorCode.STUDYROOM_ALREADY_STARTED, e.getCode());
  }

  // cancel (#53)

  @Test
  @DisplayName("Cancelling stores the status and the reason")
  void cancelStoresReason() {
    // Act
    room.cancel("tutor is sick");

    // Assert
    assertEquals(StudyRoomStatus.CANCELLED, room.getStatus());
    assertEquals("tutor is sick", room.getCancellationReason());
  }

  @Test
  @DisplayName("A cancelled room can't be cancelled again")
  void cancelTwiceIsRejected() {
    // Arrange
    room.cancel(null);

    // Act
    StudyMatchException e = assertThrows(StudyMatchException.class, () -> room.cancel(null));

    // Assert
    assertEquals(ErrorCode.STUDYROOM_CLOSED, e.getCode());
  }

  // confirm attendance (#55)

  @Test
  @DisplayName("Attendance can't be confirmed before the room ends")
  void confirmBeforeEndIsRejected() {
    StudyMatchException e = assertThrows(StudyMatchException.class, () -> room.confirmAttendance(Set.of()));
    assertEquals(ErrorCode.STUDYROOM_NOT_ENDED, e.getCode());
  }

  @Test
  @DisplayName("Confirming attendance completes the room and marks only the ticked students")
  void confirmAttendanceMarksAttendees() {
    // Arrange
    room.join(ana);
    room.join(bruno);
    moveRoomToThePast();

    // Act - only ana showed up
    room.confirmAttendance(Set.of(ana));

    // Assert
    assertEquals(StudyRoomStatus.COMPLETED, room.getStatus());
    assertTrue(participationOf(ana).isAttended());
    assertFalse(participationOf(bruno).isAttended());
  }

  @Test
  @DisplayName("Confirming with nobody completes the room with no attendees")
  void confirmWithNoAttendees() {
    // Arrange
    room.join(ana);
    moveRoomToThePast();

    // Act - also what happens after 48h without confirmation
    room.confirmAttendance(Set.of());

    // Assert
    assertEquals(StudyRoomStatus.COMPLETED, room.getStatus());
    assertFalse(participationOf(ana).isAttended());
  }

  @Test
  @DisplayName("Attendance can't be confirmed for a cancelled room")
  void confirmCancelledRoomIsRejected() {
    // Arrange
    room.cancel(null);
    moveRoomToThePast();

    // Act
    StudyMatchException e = assertThrows(StudyMatchException.class, () -> room.confirmAttendance(Set.of()));

    // Assert
    assertEquals(ErrorCode.STUDYROOM_CLOSED, e.getCode());
  }

}
