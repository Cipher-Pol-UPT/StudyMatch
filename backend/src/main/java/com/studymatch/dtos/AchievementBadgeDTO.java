package com.studymatch.dtos;

import java.time.LocalDateTime;

import com.studymatch.enums.Achievement;
import com.studymatch.models.EarnedAchievement;

public record AchievementBadgeDTO(Achievement achievement, LocalDateTime earnedAt) {

  public static AchievementBadgeDTO from(EarnedAchievement earnedAchievement) {
    return new AchievementBadgeDTO(earnedAchievement.getAchievement(), earnedAchievement.getEarnedAt());
  }

}
