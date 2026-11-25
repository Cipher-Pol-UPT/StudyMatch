package com.studymatch.services;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
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
import com.studymatch.dtos.AssignmentGroupsDTO;
import com.studymatch.dtos.GroupDTO;
import com.studymatch.dtos.GroupGenerationDTO;
import com.studymatch.dtos.MyGroupDTO;
import com.studymatch.enums.GroupStatus;
import com.studymatch.enums.Language;
import com.studymatch.exceptions.ErrorCode;
import com.studymatch.exceptions.StudyMatchException;
import com.studymatch.matching.Candidate;
import com.studymatch.matching.GroupGenerator;
import com.studymatch.matching.NoValidSplitException;
import com.studymatch.matching.UnmetSeparationsException;
import com.studymatch.models.Absence;
import com.studymatch.models.AcademicYear;
import com.studymatch.models.Assignment;
import com.studymatch.models.ClassSection;
import com.studymatch.models.Enrolment;
import com.studymatch.models.Group;
import com.studymatch.models.GroupingRule;
import com.studymatch.models.Lesson;
import com.studymatch.models.PersonalityProfile;
import com.studymatch.models.Separation;
import com.studymatch.models.Student;
import com.studymatch.models.SubjectEdition;
import com.studymatch.repositories.GroupRepository;

@Service
public class GroupService {

  private final GroupRepository groupRepository;
  private final AssignmentService assignmentService;
  private final EnrolmentService enrolmentService;
  private final AcademicYearService academicYearService;
  private final SubjectEditionService subjectEditionService;
  private final LessonService lessonService;
  private final AbsenceService absenceService;
  private final GroupGenerator groupGenerator;
  private final Random random = new SecureRandom();

  public GroupService(GroupRepository groupRepository, AssignmentService assignmentService,
      EnrolmentService enrolmentService, AcademicYearService academicYearService,
      SubjectEditionService subjectEditionService, LessonService lessonService, AbsenceService absenceService,
      GroupGenerator groupGenerator) {
    this.groupRepository = groupRepository;
    this.assignmentService = assignmentService;
    this.enrolmentService = enrolmentService;
    this.academicYearService = academicYearService;
    this.subjectEditionService = subjectEditionService;
    this.lessonService = lessonService;
    this.absenceService = absenceService;
    this.groupGenerator = groupGenerator;
  }

  // generate groups: the new groups replace the drafts, the published ones stay until the teacher confirms
  @Transactional
  public List<GroupDTO> generateGroups(Long subjectEditionId, Long assignmentId, boolean replacePublished) {
    Assignment assignment = assignmentService.getLockedAssignment(subjectEditionId, assignmentId);
    GroupingRule rule = assignment.getGroupingRule();
    if (rule == null) {
      throw new StudyMatchException(ErrorCode.ASSIGNMENT_NOT_GROUP_WORK);
    }
    if (!replacePublished && groupRepository.existsByAssignmentAndStatus(assignment, GroupStatus.PUBLISHED)) {
      throw new StudyMatchException(ErrorCode.GROUPS_ALREADY_PUBLISHED);
    }
    List<Enrolment> enrolments = groupable(subjectEditionId);
    if (enrolments.size() < rule.getMinSize()) {
      throw new StudyMatchException(ErrorCode.NOT_ENOUGH_STUDENTS_FOR_GROUPS);
    }
    List<Candidate> candidates = candidates(assignment, enrolments);
    List<List<Integer>> groups = generate(candidates, separations(rule, enrolments), rule);

    groupRepository.deleteByAssignmentAndStatus(assignment, GroupStatus.DRAFT);
    // the old drafts have to be gone before the new ones take their numbers
    groupRepository.flush();
    Map<Long, String> classOf = classSections(enrolments);
    List<GroupDTO> previews = new ArrayList<>();
    for (int index = 0; index < groups.size(); index++) {
      Group group = new Group(assignment, index + 1);
      groups.get(index).forEach(student -> group.addMember(enrolments.get(student).getStudent()));
      previews.add(toDTO(groupRepository.save(group), classOf));
    }
    return previews;
  }

  // view groups: the drafts and the published groups, no groups yet is two empty lists
  @Transactional(readOnly = true)
  public AssignmentGroupsDTO listGroups(Long subjectEditionId, Long assignmentId) {
    Assignment assignment = assignmentService.getAssignment(subjectEditionId, assignmentId);
    if (assignment.getGroupingRule() == null) {
      throw new StudyMatchException(ErrorCode.ASSIGNMENT_NOT_GROUP_WORK);
    }
    Map<Long, String> classOf = classSections(enrolmentService.listEnrolments(subjectEditionId));
    Map<GroupStatus, List<GroupDTO>> byStatus = groupRepository.findByAssignmentOrderByNumber(assignment).stream()
        .map(group -> toDTO(group, classOf))
        .collect(Collectors.groupingBy(GroupDTO::status));
    return new AssignmentGroupsDTO(byStatus.getOrDefault(GroupStatus.DRAFT, List.of()),
        byStatus.getOrDefault(GroupStatus.PUBLISHED, List.of()));
  }

  // generate groups, before generating: about how many groups and how long the search takes, for the page's loader
  @Transactional(readOnly = true)
  public GroupGenerationDTO planGroups(Long subjectEditionId, Long assignmentId) {
    Assignment assignment = assignmentService.getAssignment(subjectEditionId, assignmentId);
    GroupingRule rule = assignment.getGroupingRule();
    if (rule == null) {
      throw new StudyMatchException(ErrorCode.ASSIGNMENT_NOT_GROUP_WORK);
    }
    List<Enrolment> enrolments = groupable(subjectEditionId);
    // only the class sizes: the evidence each student's grouping needs is generate's to load
    List<Integer> classSizes = rule.isSameClass()
        ? enrolments.stream().collect(Collectors.groupingBy(enrolment -> enrolment.getClassSection().getCode(),
            Collectors.summingInt(enrolment -> 1))).values().stream().toList()
        : List.of(enrolments.size());
    GroupGenerator.Plan plan = enrolments.isEmpty() ? new GroupGenerator.Plan(0, Duration.ZERO)
        : groupGenerator.estimate(classSizes, rule.getMinSize(), rule.getOptimalSize(), rule.getMaxSize());
    return new GroupGenerationDTO(enrolments.size(), plan.groups(), rule.getOptimalSize(), plan.search().toMillis());
  }

  // generate groups, confirm: the drafts replace whatever was published before
  @Transactional
  public void publishGroups(Long subjectEditionId, Long assignmentId) {
    Assignment assignment = assignmentService.getLockedAssignment(subjectEditionId, assignmentId);
    List<Group> drafts = groupRepository.findByAssignmentAndStatusOrderByNumber(assignment, GroupStatus.DRAFT);
    if (drafts.isEmpty()) {
      throw new StudyMatchException(ErrorCode.NO_DRAFT_GROUPS);
    }
    groupRepository.deleteByAssignmentAndStatus(assignment, GroupStatus.PUBLISHED);
    groupRepository.flush();
    drafts.forEach(Group::publish);
    // telling the students about their groups comes with notifications (#144)
  }

  // generate groups, cancel: the published groups, if any, stay as they were
  @Transactional
  public void discardGroups(Long subjectEditionId, Long assignmentId) {
    Assignment assignment = assignmentService.getLockedAssignment(subjectEditionId, assignmentId);
    groupRepository.deleteByAssignmentAndStatus(assignment, GroupStatus.DRAFT);
  }

  private List<List<Integer>> generate(List<Candidate> candidates, List<int[]> separations, GroupingRule rule) {
    try {
      return groupGenerator.generate(candidates, separations, rule.getMinSize(), rule.getOptimalSize(),
          rule.getMaxSize(), rule.isSameClass(), random);
    } catch (NoValidSplitException e) {
      throw new StudyMatchException(ErrorCode.NO_VALID_GROUP_SPLIT);
    } catch (UnmetSeparationsException e) {
      throw new StudyMatchException(ErrorCode.SEPARATIONS_CANNOT_BE_MET);
    }
  }

  // blocked accounts aren't placed in new groups (GR5)
  private List<Enrolment> groupable(Long subjectEditionId) {
    return enrolmentService.listEnrolments(subjectEditionId).stream()
        .filter(enrolment -> !enrolment.getStudent().isBlocked())
        .sorted(Comparator.comparing(enrolment -> enrolment.getStudent().getNumber()))
        .toList();
  }

  private List<Candidate> candidates(Assignment assignment, List<Enrolment> enrolments) {
    Long categoryId = assignment.getSubjectEdition().getSubject().getCategory().getId();
    return enrolments.stream().map(enrolment -> toCandidate(enrolment, categoryId)).toList();
  }

  private Candidate toCandidate(Enrolment enrolment, Long categoryId) {
    Student student = enrolment.getStudent();
    EnrolmentService.GroupingEvidence evidence = enrolmentService.getGroupingEvidence(student, categoryId);
    int languages = 0;
    for (Language language : student.getLanguages()) {
      languages |= 1 << language.ordinal();
    }
    return new Candidate(traits(student.getPersonalityProfile()), evidence.level(), evidence.attendance(),
        languages, enrolment.getClassSection().getCode());
  }

  // the matching works with emotional stability, the opposite of neuroticism
  private static Candidate.Traits traits(PersonalityProfile profile) {
    if (profile == null) {
      return null;
    }
    return new Candidate.Traits(profile.getOpenness(), profile.getConscientiousness(), profile.getExtraversion(),
        profile.getAgreeableness(), 1 - profile.getNeuroticism());
  }

  // as positions in the list; a pair with a blocked student no longer matters
  private static List<int[]> separations(GroupingRule rule, List<Enrolment> enrolments) {
    Map<Long, Integer> position = new HashMap<>();
    for (int i = 0; i < enrolments.size(); i++) {
      position.put(enrolments.get(i).getStudent().getNumber(), i);
    }
    List<int[]> pairs = new ArrayList<>();
    for (Separation separation : rule.getSeparations()) {
      Integer first = position.get(separation.getFirstStudent().getNumber());
      Integer second = position.get(separation.getSecondStudent().getNumber());
      if (first != null && second != null) {
        pairs.add(new int[] { first, second });
      }
    }
    return pairs;
  }

  // each student's class now, so a student moved after generating shows in their new class
  private static Map<Long, String> classSections(List<Enrolment> enrolments) {
    return enrolments.stream().collect(Collectors.toMap(enrolment -> enrolment.getStudent().getNumber(),
        enrolment -> enrolment.getClassSection().getCode()));
  }

  // never the statute type or any grade or attendance figure, only whether attendance applies (GR7)
  private static GroupDTO toDTO(Group group, Map<Long, String> classOf) {
    Set<Language> shared = EnumSet.allOf(Language.class);
    List<GroupDTO.Member> members = new ArrayList<>();
    for (Student student : group.getMembers()) {
      shared.retainAll(student.getLanguages());
      members.add(new GroupDTO.Member(student.getNumber(), student.getName(), classOf.get(student.getNumber()),
          !student.hasActiveStatute(), student.isBlocked()));
    }
    members.sort(Comparator.comparing(GroupDTO.Member::name).thenComparing(GroupDTO.Member::number));
    return new GroupDTO(group.getId(), group.getNumber(), group.getStatus(), members.size(), List.copyOf(shared),
        members);
  }

  // view my groups: drafts can still change, so students only see published groups, latest assignment first
  @Transactional(readOnly = true)
  public List<MyGroupDTO> listMyGroups(Long studentNumber) {
    return groupRepository.findWithMember(studentNumber, GroupStatus.PUBLISHED).stream()
        .sorted(Comparator.comparing((Group group) -> group.getAssignment().getDate()).reversed()
            .thenComparing(group -> group.getAssignment().getSubjectEdition().getSubject().getName())
            .thenComparing(group -> group.getAssignment().getName())
            .thenComparing(Group::getNumber))
        .map(MyGroupDTO::from)
        .toList();
  }

  // analytics: drafts can still change, so only the published groups are measured
  public List<Group> listPublishedGroups(AcademicYear academicYear) {
    return groupRepository.findByAssignment_SubjectEdition_AcademicYearAndStatus(academicYear, GroupStatus.PUBLISHED);
  }

  // view analytics (staff): one academic year, narrowed by edition, class or group, counted in memory.
  // here because it's the one service that can use all the others it needs without a loop.
  // no year asked for means the current one; a filter that matches nothing gives empty analytics, not an error
  @Transactional(readOnly = true)
  public AnalyticsDTO getAnalytics(Integer startYear, Long subjectEditionId, String classSection, Long groupId) {
    List<Integer> years = academicYearService.listAcademicYears().stream().map(AcademicYear::getStartYear).toList();
    Optional<AcademicYear> year = startYear == null ? academicYearService.findCurrent()
        : Optional.of(academicYearService.getAcademicYear(startYear));
    if (year.isEmpty()) {
      return AnalyticsDTO.empty(years);
    }
    List<Group> groups = listPublishedGroups(year.get());
    List<Enrolment> enrolments = analyticsEnrolments(enrolmentService.listEnrolments(year.get()), subjectEditionId,
        classSection, groupId, groups);
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
        attendanceRate(lessons, missed),
        months.entrySet().stream()
            .map(month -> new MonthAttendance(month.getKey().toString(),
                attendanceRate(month.getValue()[0], month.getValue()[1])))
            .toList(),
        personalityCounts(students), groupBalance(groups, enrolments, groupId),
        !students.isEmpty() && students.stream().allMatch(student -> student.getCurricularYear() == 1),
        analyticsFilters(years, year.get(), groups));
  }

  // a group keeps to its own subject: its members' other subjects aren't about the group
  private static List<Enrolment> analyticsEnrolments(List<Enrolment> enrolments, Long subjectEditionId,
      String classSection, Long groupId, List<Group> groups) {
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
  private static Double attendanceRate(long lessons, long missed) {
    return lessons == 0 ? null : 100.0 * (lessons - missed) / lessons;
  }

  // every trait, even with no students, so the chart always has the same five
  private static List<TraitCount> personalityCounts(List<Student> students) {
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

  private Filters analyticsFilters(List<Integer> years, AcademicYear year, List<Group> groups) {
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
