package com.studymatch.dtos;

import com.studymatch.models.PersonalityProfile;

public record PersonalityProfileDTO(double openness, double conscientiousness, double extraversion,
    double agreeableness, double neuroticism) {

  public static PersonalityProfileDTO from(PersonalityProfile profile) {
    if (profile == null) {
      return null;
    }
    return new PersonalityProfileDTO(profile.getOpenness(), profile.getConscientiousness(),
        profile.getExtraversion(), profile.getAgreeableness(), profile.getNeuroticism());
  }

}
