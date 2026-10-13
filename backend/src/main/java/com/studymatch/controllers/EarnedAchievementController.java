package com.studymatch.controllers;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.studymatch.services.EarnedAchievementService;

@RestController
@RequestMapping("/achievements/")
public class EarnedAchievementController {

  private final EarnedAchievementService earnedAchievementService;

  public EarnedAchievementController(EarnedAchievementService earnedAchievementService) {
    this.earnedAchievementService = earnedAchievementService;
  }

}
