package com.studymatch.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

import com.studymatch.dtos.CreateStudyRoomRequestDTO;
import com.studymatch.dtos.ParticipantDTO;
import com.studymatch.dtos.StudyRoomDTO;
import com.studymatch.dtos.StudyRoomDetailDTO;
import com.studymatch.dtos.UpdateStudyRoomRequestDTO;
import com.studymatch.enums.NotificationTarget;
import com.studymatch.enums.NotificationType;
import com.studymatch.enums.StudyRoomStatus;
import com.studymatch.events.TutorActivityEvent;
import com.studymatch.exceptions.ErrorCode;
import com.studymatch.exceptions.StudyMatchException;
import com.studymatch.models.ModelTestData;
import com.studymatch.models.Student;
import com.studymatch.models.StudyRoom;
import com.studymatch.models.TutorRole;
import com.studymatch.repositories.StudyRoomRepository;
import com.studymatch.utils.MeetLinkGenerator;

class StudyRoomServiceTest {

  private static final Long TUTOR_NUMBER = 1L;
  private static final Long SUBJECT_ID = 10L;
  private static final Long ROOM_ID = 20L;
  private static final String LINK = "https://meet.google.com/abc-defg-hij";
  private static final String GENERATED_LINK = "https://meet.google.com/xyz-wxyz-xyz";

  private StudyRoomRepository studyRoomRepository;
  private TutorRoleService tutorRoleService;
  private RatingService ratingService;
  private EnrolmentService enrolmentService;
  private ApplicationEventPublisher eventPublisher;
  private MeetLinkGenerator meetLinkGenerator;
  private NotificationService notificationService;
  private StudyRoomService studyRoomService;

  private TutorRole tutorRole;

  @BeforeEach
  void setUp() {
    studyRoomRepository = mock(StudyRoomRepository.class);
    tutorRoleService = mock(TutorRoleService.class);
    ratingService = mock(RatingService.class);
    enrolmentService = mock(EnrolmentService.class);
    eventPublisher = mock(ApplicationEventPublisher.class);
    meetLinkGenerator = mock(MeetLinkGenerator.class);
    notificationService = mock(NotificationService.class);
    studyRoomService = new StudyRoomService(studyRoomRepository, tutorRoleService, ratingService, enrolmentService,
        eventPublisher, meetLinkGenerator, notificationService);

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

  // generate a Google Meet link (#268)

  @Test
  @DisplayName("A pasted link is saved as it is and Google is never asked")
  void pastedLinkSkipsGoogle() {
    // Arrange
    when(tutorRoleService.getByStudentAndSubject(TUTOR_NUMBER, SUBJECT_ID)).thenReturn(tutorRole);
    when(studyRoomRepository.save(any(StudyRoom.class))).thenAnswer(invocation -> invocation.getArgument(0));

    // Act
    StudyRoomDTO room = studyRoomService.create(TUTOR_NUMBER, request(tomorrowAt(14), tomorrowAt(16)));

    // Assert
    assertEquals(LINK, room.link());
    verifyNoInteractions(meetLinkGenerator);
  }

  @Test
  @DisplayName("Without a link the room gets the one Google made")
  void noLinkGetsGeneratedOne() {
    // Arrange
    when(tutorRoleService.getByStudentAndSubject(TUTOR_NUMBER, SUBJECT_ID)).thenReturn(tutorRole);
    when(studyRoomRepository.save(any(StudyRoom.class))).thenAnswer(invocation -> invocation.getArgument(0));
    when(meetLinkGenerator.createLink()).thenReturn(GENERATED_LINK);

    // Act
    StudyRoomDTO room = studyRoomService.create(TUTOR_NUMBER, requestWithoutLink(tomorrowAt(14), tomorrowAt(16)));

    // Assert
    assertEquals(GENERATED_LINK, room.link());
  }

  @Test
  @DisplayName("When Google can't make the link, MEET_LINK_UNAVAILABLE and nothing is saved")
  void googleFailsSavesNothing() {
    // Arrange
    when(tutorRoleService.getByStudentAndSubject(TUTOR_NUMBER, SUBJECT_ID)).thenReturn(tutorRole);
    when(meetLinkGenerator.createLink())
        .thenThrow(new StudyMatchException(ErrorCode.MEET_LINK_UNAVAILABLE));

    // Act
    StudyMatchException e = assertThrows(StudyMatchException.class,
        () -> studyRoomService.create(TUTOR_NUMBER, requestWithoutLink(tomorrowAt(14), tomorrowAt(16))));

    // Assert
    assertEquals(ErrorCode.MEET_LINK_UNAVAILABLE, e.getCode());
    verify(studyRoomRepository, never()).save(any());
  }

  @Test
  @DisplayName("A room that fails its checks never asks Google for a link")
  void invalidRoomSkipsGoogle() {
    // Arrange
    StudyRoom existing = new StudyRoom(tutorRole, tomorrowAt(15), tomorrowAt(17), 5, "existing room", LINK);
    ReflectionTestUtils.setField(existing, "id", 7L);
    when(tutorRoleService.getByStudentAndSubject(TUTOR_NUMBER, SUBJECT_ID)).thenReturn(tutorRole);
    when(studyRoomRepository.findOverlapping(TUTOR_NUMBER, tomorrowAt(14), tomorrowAt(16)))
        .thenReturn(List.of(existing));

    // Act
    assertThrows(StudyMatchException.class,
        () -> studyRoomService.create(TUTOR_NUMBER, requestWithoutLink(tomorrowAt(14), tomorrowAt(16))));

    // Assert - otherwise a refused room would leave an event in the calendar
    verifyNoInteractions(meetLinkGenerator);
  }

  // view StudyRooms (#43)

  @Test
  @DisplayName("The list maps the open rooms and shows the link to the host")
  void listsRooms() {
    // Arrange
    StudyRoom room = openRoom();
    when(studyRoomRepository.findAll(any(Specification.class), any(Sort.class))).thenReturn(List.of(room));

    // Act - the host browses the calendar
    List<StudyRoomDTO> rooms = studyRoomService.listRooms(TUTOR_NUMBER, null, null, null, null);

    // Assert
    assertEquals(1, rooms.size());
    assertEquals("Test Subject", rooms.get(0).subjectName());
    assertEquals(5, rooms.get(0).availablePlaces());
    assertEquals(LINK, rooms.get(0).link());
    verify(studyRoomRepository).findAll(any(Specification.class), any(Sort.class));
  }

  @Test
  @DisplayName("The list hides the link from a student who isn't the tutor and hasn't joined")
  void listHidesLink() {
    // Arrange
    StudyRoom room = openRoom(student("Ana Test", 2L));
    when(studyRoomRepository.findAll(any(Specification.class), any(Sort.class))).thenReturn(List.of(room));

    // Act - a student who isn't the host and hasn't joined
    List<StudyRoomDTO> rooms = studyRoomService.listRooms(3L, null, null, null, null);

    // Assert
    assertNull(rooms.get(0).link());
  }

  @Test
  @DisplayName("The host opens their room: hosted, not joined, and no join option")
  void hostCannotJoin() {
    // Arrange
    StudyRoom room = openRoom();
    when(studyRoomRepository.findById(ROOM_ID)).thenReturn(Optional.of(room));

    // Act
    StudyRoomDetailDTO detail = studyRoomService.getRoom(ROOM_ID, TUTOR_NUMBER);

    // Assert
    assertTrue(detail.hosted());
    assertFalse(detail.joined());
    assertFalse(detail.canJoin());
    assertEquals(LINK, detail.link());
  }

  @Test
  @DisplayName("A student who already joined can't join again")
  void joinedStudentCannotJoin() {
    // Arrange
    StudyRoom room = openRoom(student("Ana Test", 2L));
    when(studyRoomRepository.findById(ROOM_ID)).thenReturn(Optional.of(room));

    // Act
    StudyRoomDetailDTO detail = studyRoomService.getRoom(ROOM_ID, 2L);

    // Assert
    assertFalse(detail.hosted());
    assertTrue(detail.joined());
    assertFalse(detail.canJoin());
    assertEquals(LINK, detail.link());
  }

  @Test
  @DisplayName("A student enrolled in the subject's current edition who hasn't passed can join")
  void openRoomCanBeJoined() {
    // Arrange
    StudyRoom room = openRoom(student("Ana Test", 2L));
    when(studyRoomRepository.findById(ROOM_ID)).thenReturn(Optional.of(room));
    when(enrolmentService.isEnrolledInCurrentEdition(3L, tutorRole.getSubject())).thenReturn(true);
    when(enrolmentService.hasPassed(3L, tutorRole.getSubject())).thenReturn(false);

    // Act
    StudyRoomDetailDTO detail = studyRoomService.getRoom(ROOM_ID, 3L);

    // Assert
    assertFalse(detail.hosted());
    assertFalse(detail.joined());
    assertTrue(detail.canJoin());
    assertNull(detail.link());
  }

  @Test
  @DisplayName("A student not enrolled in the subject's current edition can't join")
  void notEnrolledCannotJoin() {
    // Arrange
    StudyRoom room = openRoom();
    when(studyRoomRepository.findById(ROOM_ID)).thenReturn(Optional.of(room));

    // Act - isEnrolledInCurrentEdition is false by default
    StudyRoomDetailDTO detail = studyRoomService.getRoom(ROOM_ID, 3L);

    // Assert
    assertFalse(detail.canJoin());
  }

  @Test
  @DisplayName("A student who already passed the subject can't join")
  void passedStudentCannotJoin() {
    // Arrange
    StudyRoom room = openRoom();
    when(studyRoomRepository.findById(ROOM_ID)).thenReturn(Optional.of(room));
    when(enrolmentService.isEnrolledInCurrentEdition(3L, tutorRole.getSubject())).thenReturn(true);
    when(enrolmentService.hasPassed(3L, tutorRole.getSubject())).thenReturn(true);

    // Act
    StudyRoomDetailDTO detail = studyRoomService.getRoom(ROOM_ID, 3L);

    // Assert
    assertFalse(detail.canJoin());
  }

  @Test
  @DisplayName("A full room can't be joined")
  void fullRoomCannotBeJoined() {
    // Arrange
    StudyRoom room = new StudyRoom(tutorRole, tomorrowAt(14), tomorrowAt(16), 2, "test room", LINK);
    room.join(student("Ana Test", 2L));
    room.join(student("Bruno Test", 3L));
    when(studyRoomRepository.findById(ROOM_ID)).thenReturn(Optional.of(room));

    // Act
    StudyRoomDetailDTO detail = studyRoomService.getRoom(ROOM_ID, 4L);

    // Assert
    assertFalse(detail.canJoin());
  }

  @Test
  @DisplayName("A room that already started can't be joined")
  void startedRoomCannotBeJoined() {
    // Arrange
    StudyRoom room = openRoom();
    ReflectionTestUtils.setField(room, "startsAt", LocalDateTime.now().minusMinutes(1));
    when(studyRoomRepository.findById(ROOM_ID)).thenReturn(Optional.of(room));

    // Act
    StudyRoomDetailDTO detail = studyRoomService.getRoom(ROOM_ID, 2L);

    // Assert
    assertFalse(detail.canJoin());
  }

  @Test
  @DisplayName("A cancelled room can't be joined")
  void cancelledRoomCannotBeJoined() {
    // Arrange
    StudyRoom room = openRoom();
    room.cancel("test reason");
    when(studyRoomRepository.findById(ROOM_ID)).thenReturn(Optional.of(room));

    // Act
    StudyRoomDetailDTO detail = studyRoomService.getRoom(ROOM_ID, 2L);

    // Assert
    assertFalse(detail.canJoin());
  }

  @Test
  @DisplayName("The detail carries the tutor's rating in that subject")
  void detailHasTutorRating() {
    // Arrange
    StudyRoom room = openRoom();
    when(studyRoomRepository.findById(ROOM_ID)).thenReturn(Optional.of(room));
    when(ratingService.getAverageStars(tutorRole)).thenReturn(4.5);
    when(ratingService.countRatings(tutorRole)).thenReturn(9L);

    // Act
    StudyRoomDetailDTO detail = studyRoomService.getRoom(ROOM_ID, TUTOR_NUMBER);

    // Assert
    assertEquals(4.5, detail.tutorAverageRating());
    assertEquals(9L, detail.tutorRatingCount());
  }

  @Test
  @DisplayName("A room that doesn't exist gives NOT_FOUND")
  void missingRoomDetail() {
    // Act - findById is empty by default
    StudyMatchException e = assertThrows(StudyMatchException.class, () -> studyRoomService.getRoom(ROOM_ID, 2L));

    // Assert
    assertEquals(ErrorCode.NOT_FOUND, e.getCode());
  }

  // a room tomorrow, open for the given students to join
  private StudyRoom openRoom(Student... participants) {
    StudyRoom room = new StudyRoom(tutorRole, tomorrowAt(14), tomorrowAt(16), 5, "test room", LINK);
    for (Student participant : participants) {
      room.join(participant);
    }
    return room;
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
  @DisplayName("Blocking a tutor tells each cancelled room's participants, without the block reason")
  void cancelUpcomingNotifiesParticipants() {
    // Arrange
    Student ana = student("Ana Test", 2L);
    StudyRoom room = roomWithId(openRoomWith(ana));
    when(studyRoomRepository.findUpcoming(eq(TUTOR_NUMBER), any())).thenReturn(List.of(room));

    // Act
    studyRoomService.cancelUpcoming(TUTOR_NUMBER);

    // Assert
    verify(notificationService).notifyAll(List.of(ana), NotificationType.STUDYROOM_CANCELLED,
        NotificationTarget.STUDYROOM, ROOM_ID, Map.of("subject", "Test Subject", "startsAt", tomorrowAt(14).toString()));
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

  @Test
  @DisplayName("Only rooms with at least 2 confirmed attendees count for achievements")
  void countsRoomsForAchievements() {
    // Arrange
    when(studyRoomRepository.countWithAttendees(TUTOR_NUMBER, 2)).thenReturn(3L);

    // Act
    long counted = studyRoomService.countCounted(TUTOR_NUMBER);

    // Assert
    assertEquals(3, counted);
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
    verify(eventPublisher).publishEvent(new TutorActivityEvent(tutorRole.getStudent()));
  }

  @Test
  @DisplayName("Confirming tells only the attendees that they can rate the tutor")
  void confirmNotifiesAttendees() {
    // Arrange
    Student ana = student("Ana Test", 2L);
    StudyRoom room = roomWithId(endedRoomWith(ana, student("Bruno Test", 3L)));
    when(studyRoomRepository.findById(ROOM_ID)).thenReturn(Optional.of(room));
    when(studyRoomRepository.save(room)).thenReturn(room);

    // Act
    studyRoomService.confirmAttendance(TUTOR_NUMBER, ROOM_ID, Set.of(2L));

    // Assert
    verify(notificationService).notifyAll(List.of(ana), NotificationType.STUDYROOM_RATING_OPEN,
        NotificationTarget.STUDYROOM, ROOM_ID, Map.of("subject", "Test Subject",
            "startsAt", room.getStartsAt().toString(), "tutor", "Test Tutor"));
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
    verifyNoInteractions(eventPublisher, notificationService);
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

  // join (#45)

  @Test
  @DisplayName("An eligible student joins and the room is saved with one more participant")
  void studentJoins() {
    // Arrange
    Student participant = student("Ana Test", 2L);
    StudyRoom room = openRoomWith();
    when(studyRoomRepository.findLockedById(ROOM_ID)).thenReturn(Optional.of(room));
    when(enrolmentService.isEnrolledInCurrentEdition(2L, tutorRole.getSubject())).thenReturn(true);
    when(enrolmentService.hasPassed(2L, tutorRole.getSubject())).thenReturn(false);
    when(studyRoomRepository.findJoinedOverlapping(2L, ROOM_ID, room.getStartsAt(), room.getEndsAt()))
        .thenReturn(List.of());
    when(studyRoomRepository.findOverlapping(2L, room.getStartsAt(), room.getEndsAt())).thenReturn(List.of());
    when(studyRoomRepository.save(room)).thenReturn(room);

    // Act
    StudyRoomDTO result = studyRoomService.join(participant, ROOM_ID);

    // Assert
    assertEquals(1, result.participantCount());
    assertEquals(StudyRoomStatus.SCHEDULED, result.status());
    verify(studyRoomRepository).save(room);
  }

  @Test
  @DisplayName("A student not enrolled in the subject's current edition can't join")
  void notEligibleCannotJoin() {
    // Arrange
    StudyRoom room = openRoomWith();
    when(studyRoomRepository.findLockedById(ROOM_ID)).thenReturn(Optional.of(room));
    // isEnrolledInCurrentEdition is false by default

    // Act
    StudyMatchException e = assertThrows(StudyMatchException.class,
        () -> studyRoomService.join(student("Ana Test", 2L), ROOM_ID));

    // Assert
    assertEquals(ErrorCode.NOT_ELIGIBLE_FOR_STUDYROOM, e.getCode());
    verify(studyRoomRepository, never()).save(any());
  }

  @Test
  @DisplayName("A student who passed the subject can't join")
  void passedCannotJoin() {
    // Arrange
    StudyRoom room = openRoomWith();
    when(studyRoomRepository.findLockedById(ROOM_ID)).thenReturn(Optional.of(room));
    when(enrolmentService.isEnrolledInCurrentEdition(2L, tutorRole.getSubject())).thenReturn(true);
    when(enrolmentService.hasPassed(2L, tutorRole.getSubject())).thenReturn(true);

    // Act
    StudyMatchException e = assertThrows(StudyMatchException.class,
        () -> studyRoomService.join(student("Ana Test", 2L), ROOM_ID));

    // Assert
    assertEquals(ErrorCode.NOT_ELIGIBLE_FOR_STUDYROOM, e.getCode());
    verify(studyRoomRepository, never()).save(any());
  }

  @Test
  @DisplayName("A student who already joined an overlapping room can't join")
  void overlapCannotJoin() {
    // Arrange
    StudyRoom room = openRoomWith();
    StudyRoom conflict = new StudyRoom(tutorRole, tomorrowAt(15), tomorrowAt(17), 5, "existing room", LINK);
    ReflectionTestUtils.setField(conflict, "id", 7L);
    when(studyRoomRepository.findLockedById(ROOM_ID)).thenReturn(Optional.of(room));
    when(enrolmentService.isEnrolledInCurrentEdition(2L, tutorRole.getSubject())).thenReturn(true);
    when(enrolmentService.hasPassed(2L, tutorRole.getSubject())).thenReturn(false);
    when(studyRoomRepository.findJoinedOverlapping(2L, ROOM_ID, room.getStartsAt(), room.getEndsAt()))
        .thenReturn(List.of(conflict));
    when(studyRoomRepository.findOverlapping(2L, room.getStartsAt(), room.getEndsAt())).thenReturn(List.of());

    // Act
    StudyMatchException e = assertThrows(StudyMatchException.class,
        () -> studyRoomService.join(student("Ana Test", 2L), ROOM_ID));

    // Assert
    assertEquals(ErrorCode.JOINED_STUDYROOM_OVERLAP, e.getCode());
    assertEquals(7L, e.getParams().get("id"));
    assertEquals("Test Subject", e.getParams().get("subject"));
    verify(studyRoomRepository, never()).save(any());
  }

  @Test
  @DisplayName("A tutor hosting an overlapping room can't join someone else's")
  void hostedRoomOverlapCannotJoin() {
    // Arrange
    StudyRoom room = openRoomWith();
    StudyRoom hosted = new StudyRoom(tutorRole, tomorrowAt(15), tomorrowAt(17), 5, "hosted room", LINK);
    ReflectionTestUtils.setField(hosted, "id", 8L);
    when(studyRoomRepository.findLockedById(ROOM_ID)).thenReturn(Optional.of(room));
    when(enrolmentService.isEnrolledInCurrentEdition(2L, tutorRole.getSubject())).thenReturn(true);
    when(enrolmentService.hasPassed(2L, tutorRole.getSubject())).thenReturn(false);
    when(studyRoomRepository.findJoinedOverlapping(2L, ROOM_ID, room.getStartsAt(), room.getEndsAt()))
        .thenReturn(List.of());
    when(studyRoomRepository.findOverlapping(2L, room.getStartsAt(), room.getEndsAt()))
        .thenReturn(List.of(hosted));

    // Act
    StudyMatchException e = assertThrows(StudyMatchException.class,
        () -> studyRoomService.join(student("Ana Test", 2L), ROOM_ID));

    // Assert
    assertEquals(ErrorCode.JOINED_STUDYROOM_OVERLAP, e.getCode());
    assertEquals(8L, e.getParams().get("id"));
    verify(studyRoomRepository, never()).save(any());
  }

  @Test
  @DisplayName("A room that doesn't exist gives NOT_FOUND")
  void missingRoomForJoin() {
    // Act - findLockedById is empty by default
    StudyMatchException e = assertThrows(StudyMatchException.class,
        () -> studyRoomService.join(student("Ana Test", 2L), ROOM_ID));

    // Assert
    assertEquals(ErrorCode.NOT_FOUND, e.getCode());
  }

  // leave (#47)

  @Test
  @DisplayName("A participant leaves and the room is saved with one fewer participant")
  void participantLeaves() {
    // Arrange
    Student participant = student("Ana Test", 2L);
    StudyRoom room = openRoomWith(participant);
    when(studyRoomRepository.findById(ROOM_ID)).thenReturn(Optional.of(room));
    when(studyRoomRepository.save(room)).thenReturn(room);

    // Act
    StudyRoomDTO result = studyRoomService.leave(participant, ROOM_ID);

    // Assert
    assertEquals(0, result.participantCount());
    assertEquals(StudyRoomStatus.SCHEDULED, result.status());
    // the student is out, so the meeting link is no longer theirs
    assertNull(result.link());
    verify(studyRoomRepository).save(room);
  }

  @Test
  @DisplayName("Leaving tells the tutor who left")
  void leaveNotifiesTutor() {
    // Arrange
    Student participant = student("Ana Test", 2L);
    StudyRoom room = roomWithId(openRoomWith(participant));
    when(studyRoomRepository.findById(ROOM_ID)).thenReturn(Optional.of(room));
    when(studyRoomRepository.save(room)).thenReturn(room);

    // Act
    studyRoomService.leave(participant, ROOM_ID);

    // Assert
    verify(notificationService).notify(tutorRole.getStudent(), NotificationType.STUDYROOM_LEFT,
        NotificationTarget.STUDYROOM, ROOM_ID, Map.of("subject", "Test Subject",
            "startsAt", tomorrowAt(14).toString(), "student", "Ana Test", "number", 2L));
  }

  @Test
  @DisplayName("A student who never joined can't leave")
  void notAParticipantCannotLeave() {
    // Arrange
    StudyRoom room = openRoomWith();
    when(studyRoomRepository.findById(ROOM_ID)).thenReturn(Optional.of(room));

    // Act - the model throws NOT_A_PARTICIPANT because the student isn't a participant
    StudyMatchException e = assertThrows(StudyMatchException.class,
        () -> studyRoomService.leave(student("Ana Test", 2L), ROOM_ID));

    // Assert
    assertEquals(ErrorCode.NOT_A_PARTICIPANT, e.getCode());
    verify(studyRoomRepository, never()).save(any());
    verifyNoInteractions(notificationService);
  }

  @Test
  @DisplayName("A room that doesn't exist gives NOT_FOUND")
  void missingRoomForLeave() {
    // Act - findById is empty by default
    StudyMatchException e = assertThrows(StudyMatchException.class,
        () -> studyRoomService.leave(student("Ana Test", 2L), ROOM_ID));

    // Assert
    assertEquals(ErrorCode.NOT_FOUND, e.getCode());
  }

  // edit (#51)

  @Test
  @DisplayName("A tutor edits their room and the new details come back")
  void updatesStudyRoom() {
    // Arrange
    StudyRoom room = openRoomWith();
    when(studyRoomRepository.findById(ROOM_ID)).thenReturn(Optional.of(room));
    when(studyRoomRepository.findOverlapping(TUTOR_NUMBER, tomorrowAt(15), tomorrowAt(17))).thenReturn(List.of());
    when(studyRoomRepository.save(room)).thenReturn(room);

    // Act
    StudyRoomDTO result = studyRoomService.update(TUTOR_NUMBER, ROOM_ID,
        updateRequest(tomorrowAt(15), tomorrowAt(17), 6, LINK));

    // Assert
    assertEquals(tomorrowAt(15), result.startsAt());
    assertEquals(tomorrowAt(17), result.endsAt());
    assertEquals(6, result.capacity());
    assertEquals(LINK, result.link());
    verify(studyRoomRepository).save(room);
  }

  @Test
  @DisplayName("Editing tells the participants the room's new times")
  void updateNotifiesParticipants() {
    // Arrange
    Student ana = student("Ana Test", 2L);
    StudyRoom room = roomWithId(openRoomWith(ana));
    when(studyRoomRepository.findById(ROOM_ID)).thenReturn(Optional.of(room));
    when(studyRoomRepository.save(room)).thenReturn(room);

    // Act
    studyRoomService.update(TUTOR_NUMBER, ROOM_ID, updateRequest(tomorrowAt(15), tomorrowAt(17), 6, LINK));

    // Assert
    verify(notificationService).notifyAll(List.of(ana), NotificationType.STUDYROOM_UPDATED,
        NotificationTarget.STUDYROOM, ROOM_ID, Map.of("subject", "Test Subject",
            "startsAt", tomorrowAt(15).toString(), "endsAt", tomorrowAt(17).toString()));
  }

  @Test
  @DisplayName("A student who doesn't host the room gets ACCESS_DENIED")
  void notHostCannotUpdate() {
    // Arrange
    StudyRoom room = openRoomWith();
    when(studyRoomRepository.findById(ROOM_ID)).thenReturn(Optional.of(room));

    // Act
    StudyMatchException e = assertThrows(StudyMatchException.class,
        () -> studyRoomService.update(2L, ROOM_ID, updateRequest(tomorrowAt(15), tomorrowAt(17), 6, LINK)));

    // Assert
    assertEquals(ErrorCode.ACCESS_DENIED, e.getCode());
    verify(studyRoomRepository, never()).save(any());
  }

  @Test
  @DisplayName("Editing to overlap another room of the tutor gets STUDYROOM_OVERLAP naming that room")
  void overlapCannotUpdate() {
    // Arrange
    StudyRoom room = openRoomWith();
    StudyRoom existing = new StudyRoom(tutorRole, tomorrowAt(16), tomorrowAt(18), 5, "existing room", LINK);
    ReflectionTestUtils.setField(existing, "id", 8L);
    when(studyRoomRepository.findById(ROOM_ID)).thenReturn(Optional.of(room));
    when(studyRoomRepository.findOverlapping(TUTOR_NUMBER, tomorrowAt(15), tomorrowAt(17)))
        .thenReturn(List.of(existing));

    // Act
    StudyMatchException e = assertThrows(StudyMatchException.class,
        () -> studyRoomService.update(TUTOR_NUMBER, ROOM_ID,
            updateRequest(tomorrowAt(15), tomorrowAt(17), 6, LINK)));

    // Assert
    assertEquals(ErrorCode.STUDYROOM_OVERLAP, e.getCode());
    assertEquals(8L, e.getParams().get("id"));
  }

  @Test
  @DisplayName("An overlapping edit doesn't ask Google for a new link")
  void overlapDoesNotGenerateLink() {
    // Arrange
    StudyRoom room = openRoomWith();
    StudyRoom existing = new StudyRoom(tutorRole, tomorrowAt(16), tomorrowAt(18), 5, "existing room", LINK);
    ReflectionTestUtils.setField(existing, "id", 8L);
    when(studyRoomRepository.findById(ROOM_ID)).thenReturn(Optional.of(room));
    when(studyRoomRepository.findOverlapping(TUTOR_NUMBER, tomorrowAt(15), tomorrowAt(17)))
        .thenReturn(List.of(existing));

    // Act - a null link is only replaced once every check passed
    StudyMatchException e = assertThrows(StudyMatchException.class,
        () -> studyRoomService.update(TUTOR_NUMBER, ROOM_ID,
            updateRequest(tomorrowAt(15), tomorrowAt(17), 6, null)));

    // Assert
    assertEquals(ErrorCode.STUDYROOM_OVERLAP, e.getCode());
    verify(meetLinkGenerator, never()).createLink();
  }

  @Test
  @DisplayName("Moving a room to a time that overlaps only its own old slot isn't an overlap")
  void overlappingOwnSlotIsNotAnOverlap() {
    // Arrange - the only "overlap" is the room's own old 14-16 slot, which shares its id
    StudyRoom room = openRoomWith();
    StudyRoom ownOldSlot = new StudyRoom(tutorRole, tomorrowAt(14), tomorrowAt(16), 5, "test room", LINK);
    ReflectionTestUtils.setField(ownOldSlot, "id", ROOM_ID);
    when(studyRoomRepository.findById(ROOM_ID)).thenReturn(Optional.of(room));
    when(studyRoomRepository.findOverlapping(TUTOR_NUMBER, tomorrowAt(15), tomorrowAt(17)))
        .thenReturn(List.of(ownOldSlot));
    when(studyRoomRepository.save(room)).thenReturn(room);

    // Act
    StudyRoomDTO result = studyRoomService.update(TUTOR_NUMBER, ROOM_ID,
        updateRequest(tomorrowAt(15), tomorrowAt(17), 5, LINK));

    // Assert
    assertEquals(tomorrowAt(15), result.startsAt());
    verify(studyRoomRepository).save(room);
  }

  @Test
  @DisplayName("Capacity can't go below the number of participants")
  void capacityBelowParticipants() {
    // Arrange
    StudyRoom room = openRoomWith(student("Ana Test", 2L), student("Bruno Test", 3L));
    when(studyRoomRepository.findById(ROOM_ID)).thenReturn(Optional.of(room));

    // Act - the model rejects capacity 1 with 2 participants
    StudyMatchException e = assertThrows(StudyMatchException.class,
        () -> studyRoomService.update(TUTOR_NUMBER, ROOM_ID,
            updateRequest(tomorrowAt(15), tomorrowAt(17), 1, LINK)));

    // Assert
    assertEquals(ErrorCode.CAPACITY_BELOW_PARTICIPANTS, e.getCode());
    verify(studyRoomRepository, never()).save(any());
  }

  @Test
  @DisplayName("A room that already started can't be edited")
  void updateAfterStartIsRejected() {
    // Arrange
    StudyRoom room = openRoomWith();
    ReflectionTestUtils.setField(room, "startsAt", LocalDateTime.now().minusMinutes(1));
    when(studyRoomRepository.findById(ROOM_ID)).thenReturn(Optional.of(room));

    // Act
    StudyMatchException e = assertThrows(StudyMatchException.class,
        () -> studyRoomService.update(TUTOR_NUMBER, ROOM_ID,
            updateRequest(tomorrowAt(15), tomorrowAt(17), 6, LINK)));

    // Assert
    assertEquals(ErrorCode.STUDYROOM_ALREADY_STARTED, e.getCode());
    verify(studyRoomRepository, never()).save(any());
  }

  @Test
  @DisplayName("A cancelled room can't be edited")
  void updateCancelledRoomIsRejected() {
    // Arrange
    StudyRoom room = openRoomWith();
    room.cancel("test reason");
    when(studyRoomRepository.findById(ROOM_ID)).thenReturn(Optional.of(room));

    // Act
    StudyMatchException e = assertThrows(StudyMatchException.class,
        () -> studyRoomService.update(TUTOR_NUMBER, ROOM_ID,
            updateRequest(tomorrowAt(15), tomorrowAt(17), 6, LINK)));

    // Assert
    assertEquals(ErrorCode.STUDYROOM_CLOSED, e.getCode());
    verify(studyRoomRepository, never()).save(any());
  }

  @Test
  @DisplayName("Without a link the room is edited with the Meet link the server made")
  void updatesWithGeneratedLink() {
    // Arrange
    StudyRoom room = openRoomWith();
    when(studyRoomRepository.findById(ROOM_ID)).thenReturn(Optional.of(room));
    when(studyRoomRepository.findOverlapping(TUTOR_NUMBER, tomorrowAt(15), tomorrowAt(17))).thenReturn(List.of());
    when(meetLinkGenerator.createLink()).thenReturn(GENERATED_LINK);
    when(studyRoomRepository.save(room)).thenReturn(room);

    // Act
    StudyRoomDTO result = studyRoomService.update(TUTOR_NUMBER, ROOM_ID,
        updateRequest(tomorrowAt(15), tomorrowAt(17), 6, null));

    // Assert
    assertEquals(GENERATED_LINK, result.link());
    verify(meetLinkGenerator).createLink();
  }

  @Test
  @DisplayName("A room that doesn't exist gives NOT_FOUND")
  void missingRoomForUpdate() {
    // Act - findById is empty by default
    StudyMatchException e = assertThrows(StudyMatchException.class,
        () -> studyRoomService.update(TUTOR_NUMBER, ROOM_ID,
            updateRequest(tomorrowAt(15), tomorrowAt(17), 6, LINK)));

    // Assert
    assertEquals(ErrorCode.NOT_FOUND, e.getCode());
  }

  // cancel (#53)

  @Test
  @DisplayName("A tutor cancels their room and the reason is kept")
  void cancelsStudyRoom() {
    // Arrange
    StudyRoom room = openRoomWith();
    when(studyRoomRepository.findById(ROOM_ID)).thenReturn(Optional.of(room));
    when(studyRoomRepository.save(room)).thenReturn(room);

    // Act
    StudyRoomDTO result = studyRoomService.cancel(TUTOR_NUMBER, ROOM_ID, "tutor is sick");

    // Assert
    assertEquals(StudyRoomStatus.CANCELLED, result.status());
    assertEquals("tutor is sick", room.getCancellationReason());
    verify(studyRoomRepository).save(room);
  }

  @Test
  @DisplayName("Cancelling tells the participants, with the reason")
  void cancelNotifiesParticipantsWithReason() {
    // Arrange
    Student ana = student("Ana Test", 2L);
    StudyRoom room = roomWithId(openRoomWith(ana));
    when(studyRoomRepository.findById(ROOM_ID)).thenReturn(Optional.of(room));
    when(studyRoomRepository.save(room)).thenReturn(room);

    // Act
    studyRoomService.cancel(TUTOR_NUMBER, ROOM_ID, "tutor is sick");

    // Assert
    verify(notificationService).notifyAll(List.of(ana), NotificationType.STUDYROOM_CANCELLED,
        NotificationTarget.STUDYROOM, ROOM_ID, Map.of("subject", "Test Subject",
            "startsAt", tomorrowAt(14).toString(), "reason", "tutor is sick"));
  }

  @Test
  @DisplayName("Cancelling without a reason still works")
  void cancelsWithoutReason() {
    // Arrange
    StudyRoom room = openRoomWith();
    when(studyRoomRepository.findById(ROOM_ID)).thenReturn(Optional.of(room));
    when(studyRoomRepository.save(room)).thenReturn(room);

    // Act
    StudyRoomDTO result = studyRoomService.cancel(TUTOR_NUMBER, ROOM_ID, null);

    // Assert
    assertEquals(StudyRoomStatus.CANCELLED, result.status());
    assertNull(room.getCancellationReason());
  }

  @Test
  @DisplayName("A student who doesn't host the room can't cancel it")
  void notHostCannotCancel() {
    // Arrange
    StudyRoom room = openRoomWith();
    when(studyRoomRepository.findById(ROOM_ID)).thenReturn(Optional.of(room));

    // Act
    StudyMatchException e = assertThrows(StudyMatchException.class,
        () -> studyRoomService.cancel(2L, ROOM_ID, null));

    // Assert
    assertEquals(ErrorCode.ACCESS_DENIED, e.getCode());
    verify(studyRoomRepository, never()).save(any());
    verifyNoInteractions(notificationService);
  }

  @Test
  @DisplayName("A room that doesn't exist gives NOT_FOUND")
  void missingRoomForCancel() {
    // Act - findById is empty by default
    StudyMatchException e = assertThrows(StudyMatchException.class,
        () -> studyRoomService.cancel(TUTOR_NUMBER, ROOM_ID, null));

    // Assert
    assertEquals(ErrorCode.NOT_FOUND, e.getCode());
  }

  // a room tomorrow, open for the given students to join
  private StudyRoom openRoomWith(Student... participants) {
    StudyRoom room = new StudyRoom(tutorRole, tomorrowAt(14), tomorrowAt(16), 5, "test room", LINK);
    for (Student participant : participants) {
      room.join(participant);
    }
    return room;
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

  // the id the notifications point at
  private static StudyRoom roomWithId(StudyRoom room) {
    ReflectionTestUtils.setField(room, "id", ROOM_ID);
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

  private static CreateStudyRoomRequestDTO requestWithoutLink(LocalDateTime startsAt, LocalDateTime endsAt) {
    return new CreateStudyRoomRequestDTO(SUBJECT_ID, startsAt, endsAt, "test room", 5, null);
  }

  private static UpdateStudyRoomRequestDTO updateRequest(LocalDateTime startsAt, LocalDateTime endsAt,
      Integer capacity, String link) {
    return new UpdateStudyRoomRequestDTO(startsAt, endsAt, "test room", capacity, link);
  }

}
