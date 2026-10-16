package com.studymatch.dtos;

import java.util.List;
import java.util.Set;

import com.studymatch.enums.Language;
import com.studymatch.models.Student;

public record StudentProfileDTO(Long number, String name, String banner, String aboutMe, Set<String> skills,
    Set<String> interests, Set<Language> languages, List<TutorBadgeDTO> tutorBadges,
    List<AchievementBadgeDTO> achievements, PrivateProfileDTO privateProfile) {

  public static StudentProfileDTO from(Student student, List<TutorBadgeDTO> tutorBadges,
      List<AchievementBadgeDTO> achievements, PrivateProfileDTO privateProfile) {
    return new StudentProfileDTO(student.getNumber(), student.getName(), student.getBanner(), student.getAboutMe(),
        Set.copyOf(student.getSkills()), Set.copyOf(student.getInterests()), Set.copyOf(student.getLanguages()),
        tutorBadges, achievements, privateProfile);
  }

}
