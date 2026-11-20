package com.studymatch.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.studymatch.dtos.CreateStudyRoomRequestDTO;
import com.studymatch.dtos.ParticipantDTO;
import com.studymatch.dtos.StudyRoomDTO;
import com.studymatch.enums.StudyRoomStatus;
import com.studymatch.exceptions.ErrorCode;
import com.studymatch.exceptions.StudyMatchException;
import com.studymatch.models.ModelTestData;
import com.studymatch.models.Student;
import com.studymatch.models.StudyRoom;
import com.studymatch.models.TutorRole;
import com.studymatch.repositories.StudyRoomRepository;

class StudyRoomServiceTest {

  private static final Long TUTOR_NUMBER = 1L;
  private static final Long SUBJECT_ID = 10L;
  private static final Long ROOM_ID = 20L;
  private static final String LINK = "https://meet.google.com/abc-defg-hij";

  private StudyRoomRepository studyRoomRepository;
  private TutorRoleService tutorRoleService;
  private StudyRoomService studyRoomService;

  private TutorRole tutorRole;

  @BeforeEach
  void setUp() {
    studyRoomRepository = mock(StudyRoomRepository.class);
    tutorRoleService = mock(TutorRoleService.class);
    studyRoomService = new StudyRoomService(studyRoomRepository, tutorRoleService);

    Student tutor = ModelTestData.student("Test Tutor");
    ReflectionTestUtils.setField(tutor, "number", TUTOR_NUMBER);
    tutorRole = ModelTestData.tutorRole(tutor);
  }

  @Test
  @DisplayName("A tutor of the subject creates a SCHEDULED StudyRoom")
  void createsStudyRoom() {
    // Arrange
    when(tutorRoleService.getByStudentAndSubject(TUTOR_NUMBER, SUBJECT_ID)).thenReturn(tutorRole);
    when(studyRoomRepository.findOverlapping(TUTOR_NUMBER, tomorrowAt(14), tomorrowAt(16))).thenReturn(List.of());
    when(studyRoomRepository.save(any(StudyRoom.class))).thenAnswer(invocation -> invocation.getArgument(0));

    // Act
    StudyRoomDTO room = studyRoomService.create(TUTOR_NUMBER, request(tomorrowAt(14), tomorrowAt(16)));

    // Assert
    assertEquals("Test Subject", room.subjectName());
    assertEquals(TUTOR_NUMBER, room.tutorNumber());
    assertEquals(tomorrowAt(14), room.startsAt());
    assertEquals(tomorrowAt(16), room.endsAt());
    assertEquals(5, room.capacity());
    assertEquals(LINK, room.link());
    assertEquals(StudyRoomStatus.SCHEDULED, room.status());
    assertEquals(0, room.participantCount());
  }

  @Test
  @DisplayName("A student who doesn't tutor the subject gets ACCESS_DENIED and nothing is saved")
  void notTutorOfSubject() {
    // Arrange
    when(tutorRoleService.getByStudentAndSubject(TUTOR_NUMBER, SUBJECT_ID))
        .thenThrow(new StudyMatchException(ErrorCode.ACCESS_DENIED));

    // Act
    StudyMatchException e = assertThrows(StudyMatchException.class,
        () -> studyRoomService.create(TUTOR_NUMBER, request(tomorrowAt(14), tomorrowAt(16))));

    // Assert
    assertEquals(ErrorCode.ACCESS_DENIED, e.getCode());
    verify(studyRoomRepository, never()).save(any());
  }

  @Test
  @DisplayName("Overlapping another room of the tutor gets STUDYROOM_OVERLAP naming that room")
  void overlapsAnotherRoom() {
    // Arrange
    StudyRoom existing = new StudyRoom(tutorRole, tomorrowAt(15), tomorrowAt(17), 5, "existing room", LINK);
    ReflectionTestUtils.setField(existing, "id", 7L);
    when(tutorRoleService.getByStudentAndSubject(TUTOR_NUMBER, SUBJECT_ID)).thenReturn(tutorRole);
    when(studyRoomRepository.findOverlapping(TUTOR_NUMBER, tomorrowAt(14), tomorrowAt(16)))
        .thenReturn(List.of(existing));

    // Act
    StudyMatchException e = assertThrows(StudyMatchException.class,
        () -> studyRoomService.create(TUTOR_NUMBER, request(tomorrowAt(14), tomorrowAt(16))));

    // Assert
    assertEquals(ErrorCode.STUDYROOM_OVERLAP, e.getCode());
    assertEquals(7L, e.getParams().get("id"));
    assertEquals("Test Subject", e.getParams().get("subject"));
    assertEquals(tomorrowAt(15).toString(), e.getParams().get("startsAt"));
    assertEquals(tomorrowAt(17).toString(), e.getParams().get("endsAt"));
    verify(studyRoomRepository, never()).save(any());
  }

  @Test
  @DisplayName("Without overlapping rooms the StudyRoom is saved")
  void noOverlapSaves() {
    // Arrange
    when(tutorRoleService.getByStudentAndSubject(TUTOR_NUMBER, SUBJECT_ID)).thenReturn(tutorRole);
    when(studyRoomRepository.findOverlapping(TUTOR_NUMBER, tomorrowAt(14), tomorrowAt(16))).thenReturn(List.of());
    when(studyRoomRepository.save(any(StudyRoom.class))).thenAnswer(invocation -> invocation.getArgument(0));

    // Act
    studyRoomService.create(TUTOR_NUMBER, request(tomorrowAt(14), tomorrowAt(16)));

    // Assert
    verify(studyRoomRepository).save(any(StudyRoom.class));
  }

  // blocked tutor

  @Test
  @DisplayName("Blocking a tutor cancels their upcoming rooms without a reason and says how many")
  void cancelUpcomingRooms() {
    // Arrange
    StudyRoom first = new StudyRoom(tutorRole, tomorrowAt(14), tomorrowAt(16), 5, "test room", LINK);
    StudyRoom second = new StudyRoom(tutorRole, tomorrowAt(18), tomorrowAt(19), 5, "test room", LINK);
    when(studyRoomRepository.findUpcoming(eq(TUTOR_NUMBER), any())).thenReturn(List.of(first, second));

    // Act
    int cancelled = studyRoomService.cancelUpcoming(TUTOR_NUMBER);

    // Assert
    assertEquals(2, cancelled);
    assertEquals(StudyRoomStatus.CANCELLED, first.getStatus());
    assertEquals(StudyRoomStatus.CANCELLED, second.getStatus());
    assertNull(first.getCancellationReason());
    verify(studyRoomRepository).saveAll(List.of(first, second));
  }

  @Test
  @DisplayName("A tutor without upcoming rooms has nothing cancelled")
  void noUpcomingRooms() {
    // Act - findUpcoming is empty by default
    int cancelled = studyRoomService.cancelUpcoming(TUTOR_NUMBER);

    // Assert
    assertEquals(0, cancelled);
  }

  @Test
  @DisplayName("The upcoming rooms are counted without changing them")
  void countUpcomingRooms() {
    // Arrange
    StudyRoom room = new StudyRoom(tutorRole, tomorrowAt(14), tomorrowAt(16), 5, "test room", LINK);
    when(studyRoomRepository.findUpcoming(eq(TUTOR_NUMBER), any())).thenReturn(List.of(room));

    // Act
    int upcoming = studyRoomService.countUpcoming(TUTOR_NUMBER);

    // Assert
    assertEquals(1, upcoming);
    assertEquals(StudyRoomStatus.SCHEDULED, room.getStatus());
  }

  // confirm attendance (#55)

  @Test
  @DisplayName("The host confirms who attended and the room is saved as COMPLETED")
  void hostConfirmsAttendance() {
    // Arrange
    StudyRoom room = endedRoomWith(student("Ana Test", 2L), student("Bruno Test", 3L));
    when(studyRoomRepository.findById(ROOM_ID)).thenReturn(Optional.of(room));
    when(studyRoomRepository.save(room)).thenReturn(room);

    // Act
    StudyRoomDTO result = studyRoomService.confirmAttendance(TUTOR_NUMBER, ROOM_ID, Set.of(2L));

    // Assert
    assertEquals(StudyRoomStatus.COMPLETED, result.status());
    verify(studyRoomRepository).save(room);
  }

  @Test
  @DisplayName("A student who doesn't host the room gets ACCESS_DENIED and nothing is saved")
  void notHostCannotConfirm() {
    // Arrange
    StudyRoom room = endedRoomWith(student("Ana Test", 2L));
    when(studyRoomRepository.findById(ROOM_ID)).thenReturn(Optional.of(room));

    // Act - ana tries to confirm her own attendance
    StudyMatchException e = assertThrows(StudyMatchException.class,
        () -> studyRoomService.confirmAttendance(2L, ROOM_ID, Set.of(2L)));

    // Assert
    assertEquals(ErrorCode.ACCESS_DENIED, e.getCode());
    assertEquals(StudyRoomStatus.SCHEDULED, room.getStatus());
    verify(studyRoomRepository, never()).save(any());
  }

  @Test
  @DisplayName("A room that doesn't exist gives NOT_FOUND")
  void missingRoom() {
    // Act - findById is empty by default
    StudyMatchException e = assertThrows(StudyMatchException.class,
        () -> studyRoomService.listParticipants(TUTOR_NUMBER, ROOM_ID));

    // Assert
    assertEquals(ErrorCode.NOT_FOUND, e.getCode());
  }

  @Test
  @DisplayName("The host sees the participants sorted by name, none attended yet")
  void hostListsParticipants() {
    // Arrange
    StudyRoom room = endedRoomWith(student("Bruno Test", 3L), student("Ana Test", 2L));
    when(studyRoomRepository.findById(ROOM_ID)).thenReturn(Optional.of(room));

    // Act
    List<ParticipantDTO> participants = studyRoomService.listParticipants(TUTOR_NUMBER, ROOM_ID);

    // Assert
    assertEquals(List.of(new ParticipantDTO(2L, "Ana Test", false), new ParticipantDTO(3L, "Bruno Test", false)),
        participants);
  }

  // a room the students joined while it was upcoming, which then ended an hour ago
  private StudyRoom endedRoomWith(Student... students) {
    StudyRoom room = new StudyRoom(tutorRole, tomorrowAt(14), tomorrowAt(16), 5, "test room", LINK);
    for (Student student : students) {
      room.join(student);
    }
    ReflectionTestUtils.setField(room, "startsAt", LocalDateTime.now().minusHours(3));
    ReflectionTestUtils.setField(room, "endsAt", LocalDateTime.now().minusHours(1));
    return room;
  }

  private static Student student(String name, Long number) {
    Student student = ModelTestData.student(name);
    ReflectionTestUtils.setField(student, "number", number);
    return student;
  }

  // tomorrow at the given hour, so tests never depend on the time they run at
  private static LocalDateTime tomorrowAt(int hour) {
    return LocalDateTime.now().plusDays(1).truncatedTo(ChronoUnit.DAYS).withHour(hour);
  }

  private static CreateStudyRoomRequestDTO request(LocalDateTime startsAt, LocalDateTime endsAt) {
    return new CreateStudyRoomRequestDTO(SUBJECT_ID, startsAt, endsAt, "test room", 5, LINK);
  }

}
