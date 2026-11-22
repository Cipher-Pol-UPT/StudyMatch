package com.studymatch.dtos;

import java.time.LocalDateTime;
import java.util.List;

// approve tutor application (#81): a pending application, with what the teacher needs to decide
public record PendingTutorApplicationDTO(
    Long applicationId,
    Long studentNumber,
    String studentName,
    int finalGrade,
    int position,
    String motivation,
    List<TutorBadgeDTO> previousRatings,
    LocalDateTime submittedAt) {
}
