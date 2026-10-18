package com.studymatch.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.studymatch.dtos.AchievementBadgeDTO;
import com.studymatch.enums.Achievement;
import com.studymatch.models.EarnedAchievement;
import com.studymatch.models.ModelTestData;
import com.studymatch.models.Student;
import com.studymatch.repositories.EarnedAchievementRepository;

class EarnedAchievementServiceTest {

  private EarnedAchievementRepository earnedAchievementRepository;
  private EarnedAchievementService earnedAchievementService;

  private Student student;

  @BeforeEach
  void setUp() {
    earnedAchievementRepository = mock(EarnedAchievementRepository.class);
    earnedAchievementService = new EarnedAchievementService(earnedAchievementRepository);

    student = ModelTestData.student("Test Student");
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

}
