package com.studymatch.services;

import org.springframework.stereotype.Service;

import com.studymatch.repositories.EarnedAchievementRepository;

@Service
public class EarnedAchievementService {

  private final EarnedAchievementRepository earnedAchievementRepository;

  public EarnedAchievementService(EarnedAchievementRepository earnedAchievementRepository) {
    this.earnedAchievementRepository = earnedAchievementRepository;
  }

}
