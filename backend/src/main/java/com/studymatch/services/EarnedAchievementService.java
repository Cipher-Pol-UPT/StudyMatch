package com.studymatch.services;

import java.util.List;

import org.springframework.stereotype.Service;

import com.studymatch.dtos.AchievementBadgeDTO;
import com.studymatch.models.Student;
import com.studymatch.repositories.EarnedAchievementRepository;

@Service
public class EarnedAchievementService {

  private final EarnedAchievementRepository earnedAchievementRepository;

  public EarnedAchievementService(EarnedAchievementRepository earnedAchievementRepository) {
    this.earnedAchievementRepository = earnedAchievementRepository;
  }

  public List<AchievementBadgeDTO> getAchievements(Student student) {
    return earnedAchievementRepository.findByStudentOrderByEarnedAtDesc(student).stream()
        .map(AchievementBadgeDTO::from)
        .toList();
  }

}
