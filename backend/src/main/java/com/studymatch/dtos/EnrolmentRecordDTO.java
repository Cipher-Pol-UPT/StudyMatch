package com.studymatch.dtos;

import com.studymatch.enums.EnrolmentStatus;
import com.studymatch.models.Enrolment;
import com.studymatch.models.Subject;

// finalGrade is null while the subject is still in progress; the edition id is for changing class
public record EnrolmentRecordDTO(Long subjectEditionId, String subjectCode, String subjectName, int ects,
    int academicYear, String classSection, EnrolmentStatus status, Integer finalGrade) {

  public static EnrolmentRecordDTO from(Enrolment enrolment) {
    Subject subject = enrolment.getSubjectEdition().getSubject();
    return new EnrolmentRecordDTO(enrolment.getSubjectEdition().getId(), subject.getCode(), subject.getName(),
        subject.getEcts(),
        enrolment.getSubjectEdition().getAcademicYear().getStartYear(), enrolment.getClassSection().getCode(),
        enrolment.getStatus(), enrolment.getFinalGrade());
  }

}
