package com.studymatch.dtos;

import com.studymatch.models.Student;

// emailSent false means the account exists but the credentials didn't go out, so staff can resend them
public record RegisteredStudentDTO(Long number, String name, String institutionalEmail, String degreeProgramme,
    int curricularYear, boolean emailSent) {

  public static RegisteredStudentDTO from(Student student, boolean emailSent) {
    return new RegisteredStudentDTO(student.getNumber(), student.getName(), student.getInstitutionalEmail(),
        student.getDegree().getName(), student.getCurricularYear(), emailSent);
  }

}
