package com.studymatch.dtos;

import com.studymatch.models.Teacher;

public record TeacherSummaryDTO(Long number, String name, String institutionalEmail) {

  public static TeacherSummaryDTO from(Teacher teacher) {
    return new TeacherSummaryDTO(teacher.getNumber(), teacher.getName(), teacher.getInstitutionalEmail());
  }

}
