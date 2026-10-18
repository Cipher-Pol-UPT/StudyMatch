package com.studymatch.dtos;

public record PrivateProfileDTO(String institutionalEmail, String degree, int curricularYear,
    PersonalityProfileDTO personalityProfile, Double attendance, boolean hasAcademicHistory) {

}
