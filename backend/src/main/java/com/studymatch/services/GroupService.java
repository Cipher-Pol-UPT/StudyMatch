package com.studymatch.services;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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
import com.studymatch.dtos.EditGroupsRequestDTO;
import com.studymatch.dtos.GroupDTO;
import com.studymatch.dtos.GroupGenerationDTO;
import com.studymatch.dtos.GroupImportDTO;
import com.studymatch.dtos.MyGroupDTO;
import com.studymatch.enums.ExportFormat;
import com.studymatch.enums.GroupStatus;
import com.studymatch.enums.Language;
import com.studymatch.enums.NotificationTarget;
import com.studymatch.enums.NotificationType;
import com.studymatch.exceptions.ErrorCode;
import com.studymatch.exceptions.StudyMatchException;
import com.studymatch.matching.Candidate;
import com.studymatch.matching.GroupGenerator;
import com.studymatch.matching.MatchingProblem;
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
import com.studymatch.utils.GroupCsvReader;
import com.studymatch.utils.GroupCsvWriter;
import com.studymatch.utils.GroupPdfWriter;

@Service
public class GroupService {

  private final GroupRepository groupRepository;
  private final AssignmentService assignmentService;
  private final EnrolmentService enrolmentService;
  private final AcademicYearService academicYearService;
  private final SubjectEditionService subjectEditionService;
  private final LessonService lessonService;
  private final AbsenceService absenceService;
  private final ChatService chatService;
  private final MessageService messageService;
  private final GroupGenerator groupGenerator;
  private final GroupCsvWriter groupCsvWriter;
  private final GroupPdfWriter groupPdfWriter;
  private final NotificationService notificationService;
  private final Random random = new SecureRandom();

  public GroupService(GroupRepository groupRepository, AssignmentService assignmentService,
      EnrolmentService enrolmentService, AcademicYearService academicYearService,
      SubjectEditionService subjectEditionService, LessonService lessonService, AbsenceService absenceService,
      ChatService chatService, MessageService messageService, GroupGenerator groupGenerator,
      GroupCsvWriter groupCsvWriter, GroupPdfWriter groupPdfWriter, NotificationService notificationService) {
    this.groupRepository = groupRepository;
    this.assignmentService = assignmentService;
    this.enrolmentService = enrolmentService;
    this.academicYearService = academicYearService;
    this.subjectEditionService = subjectEditionService;
    this.lessonService = lessonService;
    this.absenceService = absenceService;
    this.chatService = chatService;
    this.messageService = messageService;
    this.groupGenerator = groupGenerator;
    this.groupCsvWriter = groupCsvWriter;
    this.groupPdfWriter = groupPdfWriter;
    this.notificationService = notificationService;
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

  // import groups: the file's groups become drafts, confirming and cancelling are generate's publish and discard.
  // the matching algorithm doesn't run, the students the file leaves out are grouped among themselves
  @Transactional
  public GroupImportDTO importGroups(Long subjectEditionId, Long assignmentId, List<GroupCsvReader.Row> rows,
      Set<Long> knownStudents, boolean replacePublished) {
    Assignment assignment = assignmentService.getLockedAssignment(subjectEditionId, assignmentId);
    GroupingRule rule = assignment.getGroupingRule();
    if (rule == null) {
      throw new StudyMatchException(ErrorCode.ASSIGNMENT_NOT_GROUP_WORK);
    }
    if (!replacePublished && groupRepository.existsByAssignmentAndStatus(assignment, GroupStatus.PUBLISHED)) {
      throw new StudyMatchException(ErrorCode.GROUPS_ALREADY_PUBLISHED);
    }
    checkImportRows(rows, knownStudents);

    List<Enrolment> enrolments = enrolmentService.listEnrolments(subjectEditionId);
    Map<Long, Student> enrolled = enrolments.stream().map(Enrolment::getStudent)
        .collect(Collectors.toMap(Student::getNumber, Function.identity()));
    if (rows.stream().noneMatch(row -> enrolled.containsKey(row.studentNumber()))) {
      throw new StudyMatchException(ErrorCode.GROUP_FILE_NOT_THIS_EDITION);
    }
    List<GroupImportDTO.Problem> skipped = new ArrayList<>();
    Map<Integer, List<Student>> fromFile = new TreeMap<>();
    Set<Long> seen = new HashSet<>();
    for (GroupCsvReader.Row row : rows) {
      Student student = enrolled.get(row.studentNumber());
      if (student == null) {
        skipped.add(new GroupImportDTO.Problem(row.row(), row.studentNumber(), GroupImportDTO.Reason.NOT_ENROLLED));
      } else if (student.isBlocked()) {
        // blocked accounts aren't placed in new groups (GR5)
        skipped.add(new GroupImportDTO.Problem(row.row(), row.studentNumber(), GroupImportDTO.Reason.BLOCKED));
      } else if (seen.add(student.getNumber())) { // twice in the same group counts once
        fromFile.computeIfAbsent(row.groupNumber(), number -> new ArrayList<>()).add(student);
      }
    }

    // a group of one isn't a group, its student joins the leftover ones
    List<List<Student>> imported = fromFile.values().stream().filter(members -> members.size() > 1).toList();
    Set<Long> grouped = imported.stream().flatMap(List::stream).map(Student::getNumber).collect(Collectors.toSet());
    List<Student> leftover = enrolled.values().stream()
        .filter(student -> !student.isBlocked() && !grouped.contains(student.getNumber()))
        .sorted(Comparator.comparing(Student::getNumber))
        .toList();

    // as many leftover students as the sizes allow form groups, the rest go where members are missing
    int formed = leftover.size();
    while (formed > 0
        && MatchingProblem.groupCount(formed, rule.getMinSize(), rule.getOptimalSize(), rule.getMaxSize()) < 1) {
      formed--;
    }
    List<List<Student>> leftoverGroups = new ArrayList<>();
    int start = 0;
    for (int size : MatchingProblem.groupSizes(formed, rule.getMinSize(), rule.getOptimalSize(), rule.getMaxSize())) {
      leftoverGroups.add(new ArrayList<>(leftover.subList(start, start + size)));
      start += size;
    }
    List<Student> placed = leftover.subList(formed, leftover.size());
    List<List<Student>> needMembers = imported.isEmpty() ? leftoverGroups : imported;
    if (!placed.isEmpty() && needMembers.isEmpty()) {
      throw new StudyMatchException(ErrorCode.NOT_ENOUGH_STUDENTS_FOR_GROUPS);
    }
    // always the smallest group, the first one on a tie, like generate fills them
    for (Student student : placed) {
      needMembers.stream().min(Comparator.comparingInt(List::size)).orElseThrow().add(student);
    }

    groupRepository.deleteByAssignmentAndStatus(assignment, GroupStatus.DRAFT);
    // the old drafts have to be gone before the new ones take their numbers
    groupRepository.flush();
    Map<Long, String> classOf = classSections(enrolments);
    List<GroupImportDTO.ImportedGroup> groups = new ArrayList<>();
    for (List<Student> members : imported) {
      groups.add(new GroupImportDTO.ImportedGroup(GroupImportDTO.Origin.IMPORTED,
          toDTO(saveDraft(assignment, groups.size() + 1, members), classOf)));
    }
    for (List<Student> members : leftoverGroups) {
      groups.add(new GroupImportDTO.ImportedGroup(GroupImportDTO.Origin.LEFTOVER,
          toDTO(saveDraft(assignment, groups.size() + 1, members), classOf)));
    }
    return new GroupImportDTO(grouped.size(), skipped, leftover.size(),
        placed.stream().map(Student::getNumber).toList(), groups);
  }

  // every problem at once with its row, so the teacher fixes the file in one go
  private static void checkImportRows(List<GroupCsvReader.Row> rows, Set<Long> knownStudents) {
    List<GroupImportDTO.Problem> problems = new ArrayList<>();
    Map<Long, Integer> groupOf = new HashMap<>();
    for (GroupCsvReader.Row row : rows) {
      if (row.groupNumber() == null || row.studentNumber() == null) {
        problems.add(new GroupImportDTO.Problem(row.row(), row.studentNumber(), GroupImportDTO.Reason.INVALID));
      } else if (!knownStudents.contains(row.studentNumber())) {
        problems.add(new GroupImportDTO.Problem(row.row(), row.studentNumber(),
            GroupImportDTO.Reason.UNKNOWN_STUDENT));
      } else {
        Integer group = groupOf.putIfAbsent(row.studentNumber(), row.groupNumber());
        if (group != null && !group.equals(row.groupNumber())) {
          problems.add(new GroupImportDTO.Problem(row.row(), row.studentNumber(),
              GroupImportDTO.Reason.IN_TWO_GROUPS));
        }
      }
    }
    if (!problems.isEmpty()) {
      throw new StudyMatchException(ErrorCode.GROUP_IMPORT_INVALID_ROWS, Map.of("rows", problems));
    }
  }

  private Group saveDraft(Assignment assignment, int number, List<Student> members) {
    Group group = new Group(assignment, number);
    members.forEach(group::addMember);
    return groupRepository.save(group);
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

  // export groups: only the published ones, the drafts can still change. Never the statute (GR7)
  @Transactional(readOnly = true)
  public byte[] exportGroups(Long subjectEditionId, Long assignmentId, ExportFormat format, String language) {
    Assignment assignment = assignmentService.getAssignment(subjectEditionId, assignmentId);
    if (assignment.getGroupingRule() == null) {
      throw new StudyMatchException(ErrorCode.ASSIGNMENT_NOT_GROUP_WORK);
    }
    List<Group> groups = groupRepository.findWithMembersByAssignmentAndStatusOrderByNumber(assignment,
        GroupStatus.PUBLISHED);
    if (groups.isEmpty()) {
      throw new StudyMatchException(ErrorCode.NO_GROUPS_TO_EXPORT);
    }
    List<GroupCsvWriter.Row> rows = groups.stream()
        .flatMap(group -> group.getMembers().stream()
            .sorted(Comparator.comparing(Student::getName).thenComparing(Student::getNumber))
            .map(student -> new GroupCsvWriter.Row(group.getNumber(), student.getNumber(), student.getName(),
                student.getInstitutionalEmail())))
        .toList();
    return switch (format) {
      case CSV -> groupCsvWriter.write(rows);
      case PDF -> groupPdfWriter.write(assignment.getSubjectEdition().getSubject().getName(), assignment.getName(),
          rows, Locale.forLanguageTag("pt".equals(language) ? "pt-PT" : "en-GB"));
    };
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

  // generate groups, confirm: the drafts replace whatever was published before, chats and their history too.
  // they're new groups with other people, each gets a fresh chat (#236)
  @Transactional
  public void publishGroups(Long subjectEditionId, Long assignmentId) {
    Assignment assignment = assignmentService.getLockedAssignment(subjectEditionId, assignmentId);
    // with their members, so telling them is one select and not one per group
    List<Group> drafts = groupRepository.findWithMembersByAssignmentAndStatusOrderByNumber(assignment,
        GroupStatus.DRAFT);
    if (drafts.isEmpty()) {
      throw new StudyMatchException(ErrorCode.NO_DRAFT_GROUPS);
    }
    messageService.deleteGroupChats(
        groupRepository.findByAssignmentAndStatusOrderByNumber(assignment, GroupStatus.PUBLISHED));
    groupRepository.deleteByAssignmentAndStatus(assignment, GroupStatus.PUBLISHED);
    groupRepository.flush();
    drafts.forEach(Group::publish);
    chatService.createGroupChats(drafts);
    // every member is told, a republish too: their group may have changed
    drafts.forEach(group -> notificationService.notifyAll(group.getMembers(), NotificationType.GROUP_PUBLISHED,
        NotificationTarget.GROUP, group.getId(), Map.of(
            "subject", assignment.getSubjectEdition().getSubject().getName(),
            "assignment", assignment.getName(),
            "number", group.getNumber())));
  }

  // generate groups, cancel: the published groups, if any, stay as they were
  @Transactional
  public void discardGroups(Long subjectEditionId, Long assignmentId) {
    Assignment assignment = assignmentService.getLockedAssignment(subjectEditionId, assignmentId);
    groupRepository.deleteByAssignmentAndStatus(assignment, GroupStatus.DRAFT);
  }

  // edit groups: sizes and constraints only warn on the page. A kept group keeps its row, so its chat stays (#236)
  @Transactional
  public List<GroupDTO> editGroups(Long subjectEditionId, Long assignmentId, GroupStatus status,
      List<EditGroupsRequestDTO.EditedGroup> edited) {
    Assignment assignment = assignmentService.getLockedAssignment(subjectEditionId, assignmentId);
    if (assignment.getGroupingRule() == null) {
      throw new StudyMatchException(ErrorCode.ASSIGNMENT_NOT_GROUP_WORK);
    }
    List<Group> current = groupRepository.findWithMembersByAssignmentAndStatusOrderByNumber(assignment, status);
    if (current.isEmpty()) {
      throw new StudyMatchException(status == GroupStatus.DRAFT ? ErrorCode.NO_DRAFT_GROUPS
          : ErrorCode.NO_PUBLISHED_GROUPS);
    }
    List<Enrolment> enrolments = enrolmentService.listEnrolments(subjectEditionId);
    List<List<Student>> members = editedMembers(edited, enrolments, current);

    // what's left in removed at the end is every group the teacher took out
    Map<Long, Group> removed = current.stream().collect(Collectors.toMap(Group::getId, Function.identity()));
    List<Group> kept = new ArrayList<>();
    for (EditGroupsRequestDTO.EditedGroup group : edited) {
      Group existing = group.id() == null ? null : removed.remove(group.id());
      if (group.id() != null && existing == null) {
        throw new StudyMatchException(ErrorCode.NOT_FOUND);
      }
      kept.add(existing);
    }
    // only published groups have chats (#236)
    boolean published = status == GroupStatus.PUBLISHED;
    if (published) {
      messageService.deleteGroupChats(removed.values());
    }
    groupRepository.deleteAll(removed.values());
    groupRepository.flush();

    // one at a time and only ever down, so two groups never share a number on the way
    List<Group> survivors = kept.stream().filter(Objects::nonNull).sorted(Comparator.comparing(Group::getNumber))
        .toList();
    List<Group> renumbered = new ArrayList<>();
    for (int i = 0; i < survivors.size(); i++) {
      if (survivors.get(i).getNumber() != i + 1) {
        survivors.get(i).renumber(i + 1);
        groupRepository.flush();
        renumbered.add(survivors.get(i));
      }
    }

    int next = survivors.size();
    List<Group> saved = new ArrayList<>();
    List<Group> added = new ArrayList<>();
    Map<Group, List<Student>> before = new HashMap<>();
    for (int i = 0; i < edited.size(); i++) {
      Group group = kept.get(i);
      if (group == null) {
        group = new Group(assignment, ++next);
        if (published) {
          group.publish();
        }
      } else {
        before.put(group, new ArrayList<>(group.getMembers()));
      }
      group.replaceMembers(members.get(i));
      saved.add(groupRepository.save(group));
      if (kept.get(i) == null) {
        added.add(saved.getLast());
      }
    }
    if (published) {
      chatService.createGroupChats(added);
      messageService.groupMembersChanged(before);
      // the name has the number, after the members so it reaches who's in now
      chatService.groupsRenumbered(renumbered);
    }
    // telling the students of changed published groups comes with notifications (#144)
    Map<Long, String> classOf = classSections(enrolments);
    return saved.stream().sorted(Comparator.comparing(Group::getNumber)).map(group -> toDTO(group, classOf)).toList();
  }

  // every enrolled student who isn't blocked in exactly one group, a blocked one can stay or go (GR5)
  private static List<List<Student>> editedMembers(List<EditGroupsRequestDTO.EditedGroup> edited,
      List<Enrolment> enrolments, List<Group> current) {
    Map<Long, Student> enrolled = enrolments.stream().map(Enrolment::getStudent)
        .collect(Collectors.toMap(Student::getNumber, Function.identity()));
    Map<Long, Long> blockedIn = new HashMap<>();
    for (Group group : current) {
      group.getMembers().stream().filter(Student::isBlocked)
          .forEach(student -> blockedIn.put(student.getNumber(), group.getId()));
    }
    Set<Long> placed = new HashSet<>();
    List<List<Student>> members = new ArrayList<>();
    for (EditGroupsRequestDTO.EditedGroup group : edited) {
      List<Student> students = new ArrayList<>();
      for (Long number : group.members()) {
        Student student = enrolled.get(number);
        if (student == null) {
          throw new StudyMatchException(ErrorCode.STUDENT_NOT_ENROLLED, Map.of("number", number));
        }
        if (!placed.add(number)) {
          throw new StudyMatchException(ErrorCode.STUDENT_IN_TWO_GROUPS, Map.of("number", number));
        }
        // a new group's id is null, so it never matches, not even for a blocked student with no group
        if (student.isBlocked() && (group.id() == null || !group.id().equals(blockedIn.get(number)))) {
          throw new StudyMatchException(ErrorCode.BLOCKED_STUDENT_MOVED, Map.of("number", number));
        }
        students.add(student);
      }
      members.add(students);
    }
    List<Long> left = enrolled.values().stream()
        .filter(student -> !student.isBlocked() && !placed.contains(student.getNumber()))
        .map(Student::getNumber)
        .sorted()
        .toList();
    if (!left.isEmpty()) {
      throw new StudyMatchException(ErrorCode.STUDENT_WITHOUT_GROUP, Map.of("numbers", left));
    }
    return members;
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
