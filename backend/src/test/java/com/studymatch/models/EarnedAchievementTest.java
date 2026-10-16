package com.studymatch.models;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.studymatch.enums.Achievement;

class EarnedAchievementTest {

  private Student tutor;

  @BeforeEach
  void setUp() {
    tutor = ModelTestData.student("Tutor Test");
  }

  @Test
  @DisplayName("A new earned achievement has its student, date and a verification code")
  void newEarnedAchievement() {
    // Act
    EarnedAchievement earned = new EarnedAchievement(tutor, Achievement.FIRST_STUDYROOM);

    // Assert
    assertSame(tutor, earned.getStudent());
    assertEquals(Achievement.FIRST_STUDYROOM, earned.getAchievement());
    assertNotNull(earned.getEarnedAt());
    assertNotNull(earned.getVerificationCode());
    assertFalse(earned.getVerificationCode().isBlank());
  }

  @Test
  @DisplayName("Every earned achievement gets a different verification code")
  void verificationCodesAreUnique() {
    // Act
    EarnedAchievement first = new EarnedAchievement(tutor, Achievement.FIRST_STUDYROOM);
    EarnedAchievement second = new EarnedAchievement(tutor, Achievement.MENTOR);

    // Assert - otherwise the QR on the certificate proves nothing
    assertNotEquals(first.getVerificationCode(), second.getVerificationCode());
  }

}
