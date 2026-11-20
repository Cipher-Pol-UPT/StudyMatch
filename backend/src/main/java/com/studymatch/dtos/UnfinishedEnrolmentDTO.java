package com.studymatch.dtos;

import com.studymatch.models.Enrolment;
import com.studymatch.models.Subject;

// an enrolment still without a final grade, listed as a warning before starting a new year
public record UnfinishedEnrolmentDTO(Long studentNumber, String studentName, String subjectCode, String subjectName,
    String classSection) {

  public static UnfinishedEnrolmentDTO from(Enrolment enrolment) {
    Subject subject = enrolment.getSubjectEdition().getSubject();
    return new UnfinishedEnrolmentDTO(enrolment.getStudent().getNumber(), enrolment.getStudent().getName(),
        subject.getCode(), subject.getName(), enrolment.getClassSection().getCode());
  }

}
