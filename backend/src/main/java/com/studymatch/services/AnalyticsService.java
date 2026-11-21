package com.studymatch.services;

import java.time.YearMonth;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.studymatch.dtos.AnalyticsDTO;
import com.studymatch.dtos.AnalyticsDTO.EditionOption;
import com.studymatch.dtos.AnalyticsDTO.Filters;
import com.studymatch.dtos.AnalyticsDTO.GroupBalance;
import com.studymatch.dtos.AnalyticsDTO.GroupOption;
import com.studymatch.dtos.AnalyticsDTO.MonthAttendance;
import com.studymatch.dtos.AnalyticsDTO.TraitCount;
import com.studymatch.models.Absence;
import com.studymatch.models.AcademicYear;
import com.studymatch.models.ClassSection;
import com.studymatch.models.Enrolment;
import com.studymatch.models.Group;
import com.studymatch.models.Lesson;
import com.studymatch.models.PersonalityProfile;
import com.studymatch.models.Student;
import com.studymatch.models.SubjectEdition;

// view analytics (staff): one academic year, narrowed by edition, class or group, counted in memory
@Service
public class AnalyticsService {

  private final AcademicYearService academicYearService;
  private final SubjectEditionService subjectEditionService;
  private final EnrolmentService enrolmentService;
  private final LessonService lessonService;
  private final AbsenceService absenceService;
  private final GroupService groupService;

  public AnalyticsService(AcademicYearService academicYearService, SubjectEditionService subjectEditionService,
      EnrolmentService enrolmentService, LessonService lessonService, AbsenceService absenceService,
      GroupService groupService) {
    this.academicYearService = academicYearService;
    this.subjectEditionService = subjectEditionService;
    this.enrolmentService = enrolmentService;
    this.lessonService = lessonService;
    this.absenceService = absenceService;
    this.groupService = groupService;
  }

  // no year asked for means the current one; a filter that matches nothing gives empty analytics, not an error
  @Transactional(readOnly = true)
  public AnalyticsDTO getAnalytics(Integer startYear, Long subjectEditionId, String classSection, Long groupId) {
    List<Integer> years = academicYearService.listAcademicYears().stream().map(AcademicYear::getStartYear).toList();
    Optional<AcademicYear> year = startYear == null ? academicYearService.findCurrent()
        : Optional.of(academicYearService.getAcademicYear(startYear));
    if (year.isEmpty()) {
      return AnalyticsDTO.empty(years);
    }
    List<Group> groups = groupService.listPublishedGroups(year.get());
    List<Enrolment> enrolments = select(enrolmentService.listEnrolments(year.get()), subjectEditionId, classSection,
        groupId, groups);
    List<Student> students = enrolments.stream()
        .map(Enrolment::getStudent)
        .collect(Collectors.toMap(Student::getNumber, Function.identity(), (first, same) -> first, LinkedHashMap::new))
        .values().stream().toList();

    // the same rule as the profile: a student with an active statute has no attendance (GR7)
    List<Enrolment> counted = enrolments.stream().filter(e -> !e.getStudent().hasActiveStatute()).toList();
    Map<YearMonth, long[]> months = attendanceByMonth(counted);
    long lessons = months.values().stream().mapToLong(month -> month[0]).sum();
    long missed = months.values().stream().mapToLong(month -> month[1]).sum();

    return new AnalyticsDTO(year.get().getStartYear(), students.size(),
        (int) enrolments.stream().map(e -> e.getSubjectEdition().getId()).distinct().count(),
        rate(lessons, missed),
        months.entrySet().stream()
            .map(month -> new MonthAttendance(month.getKey().toString(), rate(month.getValue()[0], month.getValue()[1])))
            .toList(),
        personality(students), groupBalance(groups, enrolments, groupId),
        !students.isEmpty() && students.stream().allMatch(student -> student.getCurricularYear() == 1),
        filters(years, year.get(), groups));
  }

  // a group keeps to its own subject: its members' other subjects aren't about the group
  private static List<Enrolment> select(List<Enrolment> enrolments, Long subjectEditionId, String classSection,
      Long groupId, List<Group> groups) {
    Optional<Group> group = groups.stream().filter(g -> g.getId().equals(groupId)).findFirst();
    if (groupId != null && group.isEmpty()) {
      return List.of();
    }
    Set<Long> members = group.map(g -> g.getMembers().stream().map(Student::getNumber).collect(Collectors.toSet()))
        .orElse(null);
    Long editionId = group.map(g -> g.getAssignment().getSubjectEdition().getId()).orElse(subjectEditionId);
    return enrolments.stream()
        .filter(e -> editionId == null || e.getSubjectEdition().getId().equals(editionId))
        .filter(e -> classSection == null || e.getClassSection().getCode().equals(classSection.trim()))
        .filter(e -> members == null || members.contains(e.getStudent().getNumber()))
        .toList();
  }

  // per month: [lessons each student should have attended, unjustified absences], oldest month first
  private Map<YearMonth, long[]> attendanceByMonth(List<Enrolment> enrolments) {
    Map<YearMonth, long[]> months = new TreeMap<>();
    if (enrolments.isEmpty()) {
      return months;
    }
    Map<Long, List<Lesson>> lessonsByClass = lessonService.listLessons(
        enrolments.stream().map(Enrolment::getClassSection).distinct().toList()).stream()
        .collect(Collectors.groupingBy(lesson -> lesson.getClassSection().getId()));
    for (Enrolment enrolment : enrolments) {
      for (Lesson lesson : lessonsByClass.getOrDefault(enrolment.getClassSection().getId(), List.of())) {
        months.computeIfAbsent(YearMonth.from(lesson.getStartsAt()), month -> new long[2])[0]++;
      }
    }
    // only absences from the class the student is in now count, like the profile after a class change
    for (Absence absence : absenceService.listUnjustifiedAbsences(enrolments)) {
      ClassSection current = absence.getEnrolment().getClassSection();
      if (absence.getLesson().getClassSection().getId().equals(current.getId())) {
        months.computeIfAbsent(YearMonth.from(absence.getLesson().getStartsAt()), month -> new long[2])[1]++;
      }
    }
    return months;
  }

  // null before the first lesson, there's nothing to measure yet
  private static Double rate(long lessons, long missed) {
    return lessons == 0 ? null : 100.0 * (lessons - missed) / lessons;
  }

  // every trait, even with no students, so the chart always has the same five
  private static List<TraitCount> personality(List<Student> students) {
    Map<String, Long> byTrait = students.stream()
        .map(Student::getPersonalityProfile)
        .filter(Objects::nonNull)
        .collect(Collectors.groupingBy(PersonalityProfile::strongestTrait, Collectors.counting()));
    return PersonalityProfile.TRAITS.stream()
        .map(trait -> new TraitCount(trait, byTrait.getOrDefault(trait, 0L).intValue()))
        .toList();
  }

  // the published groups with a member selected in the group's own subject, so a subject or class filter keeps to it
  private List<GroupBalance> groupBalance(List<Group> groups, List<Enrolment> enrolments, Long groupId) {
    Map<Long, Set<Long>> editionsOf = enrolments.stream().collect(Collectors.groupingBy(e -> e.getStudent().getNumber(),
        Collectors.mapping(e -> e.getSubjectEdition().getId(), Collectors.toSet())));
    List<Group> shown = groups.stream()
        .filter(group -> groupId == null || group.getId().equals(groupId))
        .filter(group -> group.getMembers().stream().anyMatch(member -> editionsOf.getOrDefault(member.getNumber(),
            Set.of()).contains(group.getAssignment().getSubjectEdition().getId())))
        .sorted(Comparator.comparing((Group group) -> group.getAssignment().getSubjectEdition().getSubject().getCode())
            .thenComparing(group -> group.getAssignment().getName())
            .thenComparing(group -> group.getAssignment().getId())
            .thenComparing(Group::getNumber))
        .toList();
    Map<Long, Double> averages = enrolmentService.getAverageGrades(
        shown.stream().flatMap(group -> group.getMembers().stream()).distinct().toList());
    return shown.stream()
        .map(group -> new GroupBalance(group.getId(), group.getAssignment().getId(), group.getAssignment().getName(),
            group.getAssignment().getSubjectEdition().getSubject().getCode(), group.getNumber(),
            group.getMembers().size(), averageGrade(group, averages)))
        .toList();
  }

  // the mean of the members' averages, members with nothing finished don't count
  private static Double averageGrade(Group group, Map<Long, Double> averages) {
    List<Double> grades = group.getMembers().stream()
        .map(member -> averages.get(member.getNumber()))
        .filter(Objects::nonNull)
        .toList();
    return grades.isEmpty() ? null : grades.stream().mapToDouble(Double::doubleValue).average().orElseThrow();
  }

  private Filters filters(List<Integer> years, AcademicYear year, List<Group> groups) {
    List<EditionOption> editions = subjectEditionService.listEditions(year).stream()
        .sorted(Comparator.comparing((SubjectEdition edition) -> edition.getSubject().getName()))
        .map(edition -> new EditionOption(edition.getId(), edition.getSubject().getCode(),
            edition.getSubject().getName(),
            edition.getClassSections().stream().map(ClassSection::getCode).sorted().toList()))
        .toList();
    List<GroupOption> groupOptions = groups.stream()
        .sorted(Comparator.comparing((Group group) -> group.getAssignment().getName()).thenComparing(Group::getNumber))
        .map(group -> new GroupOption(group.getId(), group.getAssignment().getSubjectEdition().getId(),
            group.getAssignment().getName(), group.getNumber()))
        .toList();
    return new Filters(years, editions, groupOptions);
  }

}
