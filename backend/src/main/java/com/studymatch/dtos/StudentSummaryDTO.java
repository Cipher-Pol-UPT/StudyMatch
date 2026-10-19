package com.studymatch.dtos;

import com.studymatch.enums.AccountStatus;

// one row of the staff's student list; attendance and average are null when there's nothing to show yet
public record StudentSummaryDTO(Long number, String name, String degree, int curricularYear, Double attendance,
    Double averageGrade, PersonalityProfileDTO personalityProfile, AccountStatus accountStatus) {
}
