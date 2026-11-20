package com.studymatch.dtos;

// apply for tutoring (#41): one passed subject, with the rank that decides whether the student can apply
public record TutorEligibleSubjectDTO(
    Long subjectId,
    String subjectName,
    int finalGrade,
    int position,
    int editionSize,
    boolean eligible,
    boolean hasPendingApplication,
    boolean hasTutorRole,
    boolean hasCurrentEdition) {
}
