package com.studymatch.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.studymatch.dtos.CreateStudyRoomRequestDTO;
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

  // tomorrow at the given hour, so tests never depend on the time they run at
  private static LocalDateTime tomorrowAt(int hour) {
    return LocalDateTime.now().plusDays(1).truncatedTo(ChronoUnit.DAYS).withHour(hour);
  }

  private static CreateStudyRoomRequestDTO request(LocalDateTime startsAt, LocalDateTime endsAt) {
    return new CreateStudyRoomRequestDTO(SUBJECT_ID, startsAt, endsAt, "test room", 5, LINK);
  }

}
