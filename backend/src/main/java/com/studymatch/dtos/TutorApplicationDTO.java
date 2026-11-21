package com.studymatch.dtos;

import java.time.LocalDateTime;

import com.studymatch.enums.TutorApplicationStatus;
import com.studymatch.models.TutorApplication;

public record TutorApplicationDTO(
    Long id, Long subjectId, String subjectName, TutorApplicationStatus status, LocalDateTime submittedAt) {

  public static TutorApplicationDTO from(TutorApplication application) {
    return new TutorApplicationDTO(application.getId(), application.getSubject().getId(),
        application.getSubject().getName(), application.getStatus(), application.getSubmittedAt());
  }
}
