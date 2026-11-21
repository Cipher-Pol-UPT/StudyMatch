package com.studymatch.dtos;

import java.time.LocalDateTime;

import com.studymatch.enums.Achievement;
import com.studymatch.models.EarnedAchievement;

// one per achievement, earned or not. the code is only sent to its owner, for the certificate (#32)
public record MyAchievementDTO(Achievement achievement, boolean earned, LocalDateTime earnedAt,
    String verificationCode) {

  public static MyAchievementDTO earned(EarnedAchievement earnedAchievement) {
    return new MyAchievementDTO(earnedAchievement.getAchievement(), true, earnedAchievement.getEarnedAt(),
        earnedAchievement.getVerificationCode());
  }

  public static MyAchievementDTO notEarned(Achievement achievement) {
    return new MyAchievementDTO(achievement, false, null, null);
  }

}
