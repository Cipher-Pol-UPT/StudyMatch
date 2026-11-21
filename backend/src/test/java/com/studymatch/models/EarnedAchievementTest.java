package com.studymatch.models;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.time.LocalDate;

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

  @Test
  @DisplayName("A new earned achievement has no evidence until its first certificate")
  void noEvidenceAtFirst() {
    // Act
    EarnedAchievement earned = new EarnedAchievement(tutor, Achievement.MENTOR);

    // Assert
    assertNull(earned.getEvidence());
  }

  @Test
  @DisplayName("The evidence of the first certificate stays, a later one doesn't replace it")
  void firstEvidenceStays() {
    // Arrange
    EarnedAchievement earned = new EarnedAchievement(tutor, Achievement.MENTOR);
    CertificateEvidence first = new CertificateEvidence(3L, 15L, 4.5, "Test Subject", LocalDate.of(2026, 1, 10));

    // Act
    earned.recordEvidence(first);
    earned.recordEvidence(new CertificateEvidence(9L, 30L, 4.9, "Other Subject", LocalDate.of(2026, 6, 10)));

    // Assert - otherwise the paper and the verify page would disagree
    assertEquals(first, earned.getEvidence());
  }

}
