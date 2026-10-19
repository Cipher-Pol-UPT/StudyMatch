package com.studymatch.dtos;

import java.time.LocalDate;
import java.util.List;

import com.studymatch.enums.AccountStatus;
import com.studymatch.enums.StatuteType;

// staff only: the normal profile plus what students and teachers must never see (the statute type above all)
public record StudentRecordDTO(StudentProfileDTO profile, String personalEmail, AccountStatus accountStatus,
    StatuteType statuteType, LocalDate statuteEndDate, Double averageGrade, List<CategoryLevelDTO> categoryLevels,
    List<EnrolmentRecordDTO> enrolments) {
}
