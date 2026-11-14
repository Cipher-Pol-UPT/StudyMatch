package com.studymatch.dtos;

import com.studymatch.models.TutorRole;

// a subject the student tutors, the id is what the create StudyRoom form sends back
public record TutorSubjectDTO(Long subjectId, String subjectName) {

  public static TutorSubjectDTO from(TutorRole tutorRole) {
    return new TutorSubjectDTO(tutorRole.getSubject().getId(), tutorRole.getSubject().getName());
  }

}
