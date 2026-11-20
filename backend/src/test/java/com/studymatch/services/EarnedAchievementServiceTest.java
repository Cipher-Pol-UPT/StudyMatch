package com.studymatch.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import com.studymatch.dtos.AchievementBadgeDTO;
import com.studymatch.enums.Achievement;
import com.studymatch.events.TutorActivityEvent;
import com.studymatch.models.EarnedAchievement;
import com.studymatch.models.ModelTestData;
import com.studymatch.models.Student;
import com.studymatch.repositories.EarnedAchievementRepository;

class EarnedAchievementServiceTest {

  private static final Long TUTOR_NUMBER = 1L;

  private EarnedAchievementRepository earnedAchievementRepository;
  private StudyRoomService studyRoomService;
  private ParticipationService participationService;
  private TutorRoleService tutorRoleService;
  private EarnedAchievementService earnedAchievementService;

  private Student student;

  @BeforeEach
  void setUp() {
    earnedAchievementRepository = mock(EarnedAchievementRepository.class);
    studyRoomService = mock(StudyRoomService.class);
    participationService = mock(ParticipationService.class);
    tutorRoleService = mock(TutorRoleService.class);
    earnedAchievementService = new EarnedAchievementService(earnedAchievementRepository, studyRoomService,
        participationService, tutorRoleService);

    student = ModelTestData.student("Test Student");
    ReflectionTestUtils.setField(student, "number", TUTOR_NUMBER);
  }

  // what gets saved when the tutor's achievements are checked
  private List<Achievement> awarded() {
    ArgumentCaptor<EarnedAchievement> saved = ArgumentCaptor.forClass(EarnedAchievement.class);
    verify(earnedAchievementRepository, atLeast(0)).saveIfNew(saved.capture());
    return saved.getAllValues().stream().map(EarnedAchievement::getAchievement).toList();
  }

  @Test
  @DisplayName("Achievements are shown with the date they were earned, newest first")
  void achievementsAreShownNewestFirst() {
    // Arrange
    EarnedAchievement newest = new EarnedAchievement(student, Achievement.DEDICATED_TUTOR);
    EarnedAchievement oldest = new EarnedAchievement(student, Achievement.FIRST_STUDYROOM);
    when(earnedAchievementRepository.findByStudentOrderByEarnedAtDesc(student)).thenReturn(List.of(newest, oldest));

    // Act
    List<AchievementBadgeDTO> achievements = earnedAchievementService.getAchievements(student);

    // Assert
    assertEquals(Achievement.DEDICATED_TUTOR, achievements.get(0).achievement());
    assertEquals(newest.getEarnedAt(), achievements.get(0).earnedAt());
    assertEquals(Achievement.FIRST_STUDYROOM, achievements.get(1).achievement());
  }

  @Test
  @DisplayName("A student without achievements has none")
  void studentWithoutAchievementsHasNone() {
    // Act
    List<AchievementBadgeDTO> achievements = earnedAchievementService.getAchievements(student);

    // Assert
    assertTrue(achievements.isEmpty());
  }

  // award achievement (#70)

  @Test
  @DisplayName("A tutor's first counted room awards First StudyRoom")
  void firstCountedRoomAwardsFirstStudyRoom() {
    // Arrange
    when(studyRoomService.countCounted(TUTOR_NUMBER)).thenReturn(1L);

    // Act
    earnedAchievementService.awardAchievements(new TutorActivityEvent(student));

    // Assert
    assertEquals(List.of(Achievement.FIRST_STUDYROOM), awarded());
  }

  @Test
  @DisplayName("An achievement the tutor already has is not awarded again")
  void earnedAchievementIsNotAwardedAgain() {
    // Arrange
    when(studyRoomService.countCounted(TUTOR_NUMBER)).thenReturn(2L);
    when(earnedAchievementRepository.findByStudentOrderByEarnedAtDesc(student))
        .thenReturn(List.of(new EarnedAchievement(student, Achievement.FIRST_STUDYROOM)));

    // Act
    earnedAchievementService.awardAchievements(new TutorActivityEvent(student));

    // Assert
    assertTrue(awarded().isEmpty());
  }

  @Test
  @DisplayName("With no criteria met nothing is awarded")
  void noCriteriaMetAwardsNothing() {
    // Act - every count is 0 and there are no ratings
    earnedAchievementService.awardAchievements(new TutorActivityEvent(student));

    // Assert
    assertTrue(awarded().isEmpty());
  }

  @Test
  @DisplayName("Reaching 10 counted rooms at once awards First StudyRoom and Dedicated Tutor together")
  void severalAchievementsAtOnce() {
    // Arrange
    when(studyRoomService.countCounted(TUTOR_NUMBER)).thenReturn(10L);

    // Act
    earnedAchievementService.awardAchievements(new TutorActivityEvent(student));

    // Assert
    assertEquals(List.of(Achievement.FIRST_STUDYROOM, Achievement.DEDICATED_TUTOR), awarded());
  }

}
