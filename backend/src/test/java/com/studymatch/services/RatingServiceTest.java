package com.studymatch.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import com.studymatch.dtos.RateTutorRequestDTO;
import com.studymatch.dtos.RatingDTO;
import com.studymatch.enums.Achievement.SubjectRating;
import com.studymatch.events.TutorActivityEvent;
import com.studymatch.exceptions.ErrorCode;
import com.studymatch.exceptions.StudyMatchException;
import com.studymatch.models.ModelTestData;
import com.studymatch.models.Participation;
import com.studymatch.models.Rating;
import com.studymatch.models.Student;
import com.studymatch.models.StudyRoom;
import com.studymatch.models.TutorRole;
import com.studymatch.repositories.RatingRepository;

class RatingServiceTest {

  private static final Long ROOM_ID = 20L;
  private static final Long ANA_NUMBER = 2L;
  private static final Long BRUNO_NUMBER = 3L;

  private RatingRepository ratingRepository;
  private ParticipationService participationService;
  private ApplicationEventPublisher eventPublisher;
  private RatingService ratingService;

  private TutorRole tutorRole;
  private Participation anaAttended;
  private Participation brunoAbsent;

  @BeforeEach
  void setUp() {
    ratingRepository = mock(RatingRepository.class);
    participationService = mock(ParticipationService.class);
    eventPublisher = mock(ApplicationEventPublisher.class);
    ratingService = new RatingService(ratingRepository, participationService, eventPublisher);

    tutorRole = ModelTestData.tutorRole(ModelTestData.student("Test Tutor"));

    // ana and bruno joined a room that ended an hour ago, only ana came
    LocalDateTime tomorrow = LocalDateTime.now().plusDays(1);
    StudyRoom room = new StudyRoom(tutorRole, tomorrow, tomorrow.plusHours(2), 5, "test room",
        "https://meet.google.com/test-link");
    Student ana = student("Ana Test", ANA_NUMBER);
    Student bruno = student("Bruno Test", BRUNO_NUMBER);
    room.join(ana);
    room.join(bruno);
    ReflectionTestUtils.setField(room, "startsAt", LocalDateTime.now().minusHours(3));
    ReflectionTestUtils.setField(room, "endsAt", LocalDateTime.now().minusHours(1));
    room.confirmAttendance(Set.of(ANA_NUMBER));
    anaAttended = participationOf(room, ana);
    brunoAbsent = participationOf(room, bruno);
  }

  private static Student student(String name, Long number) {
    Student student = ModelTestData.student(name);
    ReflectionTestUtils.setField(student, "number", number);
    return student;
  }

  private static Participation participationOf(StudyRoom room, Student student) {
    return room.getParticipations().stream()
        .filter(p -> p.getStudent() == student)
        .findFirst()
        .orElseThrow();
  }

  // average and count (shown on the tutor's badge)

  @Test
  @DisplayName("Each student counts once: ana's 5, 5, 5 and bruno's 3 average to 4")
  void averageOfEachStudentsAverage() {
    // Arrange - the query gives one average per student
    when(ratingRepository.findAverageStarsPerStudent(tutorRole)).thenReturn(List.of(5.0, 3.0));

    // Act
    Double average = ratingService.getAverageStars(tutorRole);

    // Assert
    assertEquals(4.0, average);
  }

  @Test
  @DisplayName("With one student the average is theirs")
  void averageWithOneStudent() {
    // Arrange
    when(ratingRepository.findAverageStarsPerStudent(tutorRole)).thenReturn(List.of(4.5));

    // Act
    Double average = ratingService.getAverageStars(tutorRole);

    // Assert
    assertEquals(4.5, average);
  }

  @Test
  @DisplayName("A tutor without ratings has no average")
  void tutorWithoutRatingsHasNoAverage() {
    // Act - the query is empty by default
    Double average = ratingService.getAverageStars(tutorRole);

    // Assert
    assertNull(average);
  }

  @Test
  @DisplayName("A subject's rating for achievements is the same average, and how many students it's from")
  void subjectRatingForAchievements() {
    // Arrange - three students rated
    when(ratingRepository.findAverageStarsPerStudent(tutorRole)).thenReturn(List.of(5.0, 4.0, 4.5));

    // Act
    SubjectRating rating = ratingService.getSubjectRating(tutorRole);

    // Assert
    assertEquals(4.5, rating.average());
    assertEquals(3, rating.raters());
  }

  @Test
  @DisplayName("A subject nobody rated has no average and no raters")
  void subjectRatingWithoutRatings() {
    // Act - the query is empty by default
    SubjectRating rating = ratingService.getSubjectRating(tutorRole);

    // Assert
    assertNull(rating.average());
    assertEquals(0, rating.raters());
  }

  @Test
  @DisplayName("Counts the tutor's ratings")
  void countsTheTutorsRatings() {
    // Arrange
    when(ratingRepository.countByParticipation_StudyRoom_TutorRole(tutorRole)).thenReturn(7L);

    // Act
    long ratings = ratingService.countRatings(tutorRole);

    // Assert
    assertEquals(7, ratings);
  }

  // rate tutor (#61)

  @Test
  @DisplayName("An attendee rates the tutor and the comment is saved trimmed")
  void attendeeRatesTheTutor() {
    // Arrange
    when(participationService.getParticipation(ROOM_ID, ANA_NUMBER)).thenReturn(anaAttended);
    when(ratingRepository.saveAndFlush(any(Rating.class))).thenAnswer(invocation -> invocation.getArgument(0));

    // Act
    RatingDTO rating = ratingService.rate(ROOM_ID, ANA_NUMBER, new RateTutorRequestDTO(5, "  very clear  "));

    // Assert
    assertEquals(5, rating.stars());
    assertEquals("very clear", rating.comment());
    assertEquals("Test Subject", rating.subject());
    verify(ratingRepository).saveAndFlush(any(Rating.class));
    verify(eventPublisher).publishEvent(new TutorActivityEvent(tutorRole.getStudent()));
  }

  @Test
  @DisplayName("A blank comment is saved as no comment")
  void blankCommentIsNoComment() {
    // Arrange
    when(participationService.getParticipation(ROOM_ID, ANA_NUMBER)).thenReturn(anaAttended);
    when(ratingRepository.saveAndFlush(any(Rating.class))).thenAnswer(invocation -> invocation.getArgument(0));

    // Act
    RatingDTO rating = ratingService.rate(ROOM_ID, ANA_NUMBER, new RateTutorRequestDTO(4, "   "));

    // Assert
    assertNull(rating.comment());
  }

  @Test
  @DisplayName("Rating the same room twice gives ROOM_ALREADY_RATED and nothing is saved")
  void secondRatingIsRejected() {
    // Arrange
    when(participationService.getParticipation(ROOM_ID, ANA_NUMBER)).thenReturn(anaAttended);
    when(ratingRepository.existsByParticipation(anaAttended)).thenReturn(true);

    // Act
    StudyMatchException e = assertThrows(StudyMatchException.class,
        () -> ratingService.rate(ROOM_ID, ANA_NUMBER, new RateTutorRequestDTO(5, null)));

    // Assert
    assertEquals(ErrorCode.ROOM_ALREADY_RATED, e.getCode());
    verify(ratingRepository, never()).saveAndFlush(any());
    verifyNoInteractions(eventPublisher);
  }

  @Test
  @DisplayName("Two ratings at the same time: the unique key stops the second with ROOM_ALREADY_RATED")
  void concurrentRatingIsRejected() {
    // Arrange - the check passed, but another request saved first
    when(participationService.getParticipation(ROOM_ID, ANA_NUMBER)).thenReturn(anaAttended);
    when(ratingRepository.saveAndFlush(any(Rating.class)))
        .thenThrow(new DataIntegrityViolationException("duplicate participation"));

    // Act
    StudyMatchException e = assertThrows(StudyMatchException.class,
        () -> ratingService.rate(ROOM_ID, ANA_NUMBER, new RateTutorRequestDTO(5, null)));

    // Assert
    assertEquals(ErrorCode.ROOM_ALREADY_RATED, e.getCode());
    verifyNoInteractions(eventPublisher);
  }

  @Test
  @DisplayName("A participant who didn't attend gets ATTENDANCE_NOT_CONFIRMED and nothing is saved")
  void absentParticipantCannotRate() {
    // Arrange
    when(participationService.getParticipation(ROOM_ID, BRUNO_NUMBER)).thenReturn(brunoAbsent);

    // Act
    StudyMatchException e = assertThrows(StudyMatchException.class,
        () -> ratingService.rate(ROOM_ID, BRUNO_NUMBER, new RateTutorRequestDTO(1, null)));

    // Assert
    assertEquals(ErrorCode.ATTENDANCE_NOT_CONFIRMED, e.getCode());
    verify(ratingRepository, never()).saveAndFlush(any());
  }

  @Test
  @DisplayName("The tutor's ratings are listed without who wrote them")
  void listsTheTutorsRatings() {
    // Arrange
    Rating rating = new Rating(anaAttended, 4, "good");
    when(ratingRepository.findByParticipation_StudyRoom_TutorRole_Student_NumberOrderByRatedAtDesc(1L))
        .thenReturn(List.of(rating));

    // Act
    List<RatingDTO> ratings = ratingService.listRatings(1L);

    // Assert
    assertEquals(List.of(new RatingDTO("Test Subject", 4, "good", rating.getRatedAt())), ratings);
  }

}
