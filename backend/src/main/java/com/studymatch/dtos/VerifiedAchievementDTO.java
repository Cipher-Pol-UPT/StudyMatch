package com.studymatch.dtos;

import java.time.LocalDateTime;

import com.studymatch.enums.Achievement;
import com.studymatch.models.CertificateEvidence;
import com.studymatch.models.EarnedAchievement;

public record VerifiedAchievementDTO(Achievement achievement, LocalDateTime earnedAt, CertificateEvidence evidence) {

  public static VerifiedAchievementDTO from(EarnedAchievement earned) {
    return new VerifiedAchievementDTO(earned.getAchievement(), earned.getEarnedAt(), earned.getEvidence());
  }

}
