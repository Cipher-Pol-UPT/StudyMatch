package com.studymatch.dtos;

import com.studymatch.models.ClassSection;

// teacher is null while Staff hasn't picked one
public record SectionTeacherDTO(String classSection, TeacherSummaryDTO teacher) {

  public static SectionTeacherDTO from(ClassSection classSection) {
    return new SectionTeacherDTO(classSection.getCode(),
        classSection.getTeacher() == null ? null : TeacherSummaryDTO.from(classSection.getTeacher()));
  }

}
