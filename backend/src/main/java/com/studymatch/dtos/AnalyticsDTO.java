package com.studymatch.dtos;

import java.util.List;

// view analytics: aggregated only, never a student's statute type. academicYear is null before the first year exists
public record AnalyticsDTO(Integer academicYear, int totalStudents, int activeSubjects, Double averageAttendance,
    List<MonthAttendance> attendanceOverTime, List<TraitCount> personalityDistribution,
    List<GroupBalance> groupBalance, boolean firstYearOnly, Filters filters) {

  // month as 2026-10, only months with lessons
  public record MonthAttendance(String month, double attendance) {
  }

  // students whose strongest Big Five trait is this one
  public record TraitCount(String trait, int students) {
  }

  // averageGrade is the members' average grade, null while none of them finished a subject
  public record GroupBalance(Long groupId, String assignment, int number, int members, Double averageGrade) {
  }

  // what the filters offer for the year, whatever is selected
  public record Filters(List<Integer> academicYears, List<EditionOption> subjectEditions, List<GroupOption> groups) {
  }

  public record EditionOption(Long id, String subjectCode, String subjectName, List<String> classSections) {
  }

  public record GroupOption(Long id, Long subjectEditionId, String assignment, int number) {
  }

  public static AnalyticsDTO empty(List<Integer> academicYears) {
    return new AnalyticsDTO(null, 0, 0, null, List.of(), List.of(), List.of(), false,
        new Filters(academicYears, List.of(), List.of()));
  }

}
