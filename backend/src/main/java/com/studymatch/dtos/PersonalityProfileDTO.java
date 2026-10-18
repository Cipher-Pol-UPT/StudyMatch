package com.studymatch.dtos;

import com.studymatch.models.PersonalityProfile;

public record PersonalityProfileDTO(double leadership, double problemSolving, double creativity, double execution) {

  public static PersonalityProfileDTO from(PersonalityProfile profile) {
    return new PersonalityProfileDTO(profile.getLeadership(), profile.getProblemSolving(), profile.getCreativity(),
        profile.getExecution());
  }

}
