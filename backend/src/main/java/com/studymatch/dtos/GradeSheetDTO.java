package com.studymatch.dtos;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import com.studymatch.enums.AssignmentType;
import com.studymatch.enums.EnrolmentStatus;
import com.studymatch.models.Assignment;
import com.studymatch.models.Enrolment;
import com.studymatch.models.Grade;

// one column per assignment, one row per student; weightsComplete false means no final grades yet
public record GradeSheetDTO(List<Column> assignments, List<Row> students, boolean weightsComplete) {

  public record Column(Long id, String name, AssignmentType type, int weight) {

    public static Column from(Assignment assignment) {
      return new Column(assignment.getId(), assignment.getName(), assignment.getType(),
          assignment.getWeight().movePointRight(2).intValue());
    }

  }

  // grades by assignment id, a missing one isn't graded yet
  public record Row(Long number, String name, String classSection, Map<Long, Double> grades, Integer finalGrade,
      EnrolmentStatus status) {

    public static Row from(Enrolment enrolment, List<Grade> grades) {
      Map<Long, Double> byAssignment = grades.stream()
          .collect(Collectors.toMap(grade -> grade.getAssignment().getId(), Grade::getValue, (a, b) -> a,
              TreeMap::new));
      return new Row(enrolment.getStudent().getNumber(), enrolment.getStudent().getName(),
          enrolment.getClassSection().getCode(), byAssignment, enrolment.getFinalGrade(), enrolment.getStatus());
    }

  }

}
