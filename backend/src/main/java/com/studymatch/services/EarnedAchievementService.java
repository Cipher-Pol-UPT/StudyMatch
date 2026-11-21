package com.studymatch.services;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import com.studymatch.dtos.AchievementBadgeDTO;
import com.studymatch.dtos.MyAchievementDTO;
import com.studymatch.enums.Achievement;
import com.studymatch.enums.Achievement.TutorRecord;
import com.studymatch.events.TutorActivityEvent;
import com.studymatch.models.EarnedAchievement;
import com.studymatch.models.Student;
import com.studymatch.repositories.EarnedAchievementRepository;

@Service
public class EarnedAchievementService {

  private final EarnedAchievementRepository earnedAchievementRepository;
  private final StudyRoomService studyRoomService;
  private final ParticipationService participationService;
  private final TutorRoleService tutorRoleService;

  public EarnedAchievementService(EarnedAchievementRepository earnedAchievementRepository,
      StudyRoomService studyRoomService, ParticipationService participationService,
      TutorRoleService tutorRoleService) {
    this.earnedAchievementRepository = earnedAchievementRepository;
    this.studyRoomService = studyRoomService;
    this.participationService = participationService;
    this.tutorRoleService = tutorRoleService;
  }

  public List<AchievementBadgeDTO> getAchievements(Student student) {
    return earnedAchievementRepository.findByStudentOrderByEarnedAtDesc(student).stream()
        .map(AchievementBadgeDTO::from)
        .toList();
  }

  // every achievement, so a student with none still sees what there is to earn
  public List<MyAchievementDTO> getMyAchievements(Long studentNumber) {
    Map<Achievement, EarnedAchievement> earned = earnedAchievementRepository.findByStudentNumber(studentNumber)
        .stream()
        .collect(Collectors.toMap(EarnedAchievement::getAchievement, Function.identity()));
    return Arrays.stream(Achievement.values())
        .map(achievement -> earned.containsKey(achievement)
            ? MyAchievementDTO.earned(earned.get(achievement))
            : MyAchievementDTO.notEarned(achievement))
        .toList();
  }

  // runs inside the confirm or rating transaction. Achievements are only added, never taken away
  @EventListener
  public void awardAchievements(TutorActivityEvent event) {
    Student tutor = event.tutor();
    TutorRecord record = new TutorRecord(studyRoomService.countCounted(tutor.getNumber()),
        participationService.countStudentsTutored(tutor.getNumber()), tutorRoleService.getSubjectRatings(tutor));
    Set<Achievement> earned = earnedAchievementRepository.findByStudentOrderByEarnedAtDesc(tutor).stream()
        .map(EarnedAchievement::getAchievement)
        .collect(Collectors.toSet());
    Arrays.stream(Achievement.values())
        .filter(achievement -> !earned.contains(achievement) && achievement.isMetBy(record))
        .forEach(achievement -> earnedAchievementRepository.saveIfNew(new EarnedAchievement(tutor, achievement)));
    // telling the tutor about a new badge comes with notifications (#144)
  }

}
