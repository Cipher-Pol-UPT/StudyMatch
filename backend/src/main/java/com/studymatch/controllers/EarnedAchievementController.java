package com.studymatch.controllers;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.studymatch.dtos.MyAchievementDTO;
import com.studymatch.models.User;
import com.studymatch.services.EarnedAchievementService;
import com.studymatch.services.UserService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/achievements/")
@Tag(name = "Achievements", description = "The achievements tutors earn, given out by the system")
public class EarnedAchievementController {

  private final EarnedAchievementService earnedAchievementService;
  private final UserService userService;

  public EarnedAchievementController(EarnedAchievementService earnedAchievementService, UserService userService) {
    this.earnedAchievementService = earnedAchievementService;
    this.userService = userService;
  }

  @GetMapping("me")
  @PreAuthorize("hasRole('STUDENT')")
  @Operation(summary = "Every achievement, with the date and verification code of the ones you've earned")
  public ResponseEntity<List<MyAchievementDTO>> getMyAchievements(Authentication authentication) {
    User student = userService.getByInstitutionalEmail(authentication.getName());
    return ResponseEntity.ok(earnedAchievementService.getMyAchievements(student.getNumber()));
  }

}
