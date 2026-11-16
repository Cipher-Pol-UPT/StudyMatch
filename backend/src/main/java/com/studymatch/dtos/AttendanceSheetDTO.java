package com.studymatch.dtos;

import java.time.LocalDateTime;
import java.util.List;

import com.studymatch.models.Absence;
import com.studymatch.models.Enrolment;
import com.studymatch.models.Student;

// one lesson of one class; recorded false means nobody registered it yet, so everyone shows as present
public record AttendanceSheetDTO(String classSection, LocalDateTime startsAt, boolean recorded, List<Row> students) {

  // attendance in this subject, null before the first lesson or with a statute (shown as not applicable, GR7)
  public record Row(Long number, String name, boolean absent, boolean justified, Double attendance,
      boolean statute) {

    public static Row from(Enrolment enrolment, Absence absence, Double attendance) {
      Student student = enrolment.getStudent();
      return new Row(student.getNumber(), student.getName(), absence != null,
          absence != null && absence.isJustified(), attendance, student.hasActiveStatute());
    }

  }

}
