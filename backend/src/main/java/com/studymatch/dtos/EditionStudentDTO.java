package com.studymatch.dtos;

// one row of a teacher's student list; categoryLevel is for this subject's category, attendance only in this subject
public record EditionStudentDTO(Long number, String name, String classSection, PersonalityProfileDTO personalityProfile,
    Double categoryLevel, Double attendance) {
}
