package com.studymatch.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import com.studymatch.dtos.AssignmentGroupsDTO;
import com.studymatch.dtos.EditGroupsRequestDTO;
import com.studymatch.dtos.GroupDTO;
import com.studymatch.dtos.GroupImportDTO;
import com.studymatch.dtos.MyGroupDTO;
import com.studymatch.enums.AssignmentType;
import com.studymatch.enums.ExportFormat;
import com.studymatch.enums.GroupStatus;
import com.studymatch.enums.Language;
import com.studymatch.enums.StatuteType;
import com.studymatch.exceptions.ErrorCode;
import com.studymatch.exceptions.StudyMatchException;
import com.studymatch.matching.GroupGenerator;
import com.studymatch.models.Assignment;
import com.studymatch.models.Group;
import com.studymatch.models.GroupingRule;
import com.studymatch.models.ModelTestData;
import com.studymatch.models.Student;
import com.studymatch.models.SubjectEdition;
import com.studymatch.repositories.GroupRepository;
import com.studymatch.utils.GroupCsvReader;
import com.studymatch.utils.GroupCsvWriter;
import com.studymatch.utils.GroupPdfWriter;

class GroupServiceTest {

  private static final Long ANA = 1001L;

  private GroupRepository groupRepository;
  private AssignmentService assignmentService;
  private EnrolmentService enrolmentService;
  private GroupPdfWriter groupPdfWriter;
  private GroupService groupService;
  private SubjectEdition edition;
  private Student ana;
  private Student rui;

  @BeforeEach
  void setUp() {
    groupRepository = mock(GroupRepository.class);
    assignmentService = mock(AssignmentService.class);
    enrolmentService = mock(EnrolmentService.class);
    groupPdfWriter = mock(GroupPdfWriter.class);
    groupService = new GroupService(groupRepository, assignmentService, enrolmentService,
        mock(AcademicYearService.class), mock(SubjectEditionService.class), mock(LessonService.class),
        mock(AbsenceService.class), mock(GroupGenerator.class), new GroupCsvWriter(), groupPdfWriter);
    edition = ModelTestData.subjectEdition();
    ana = student("Ana", ANA);
    rui = student("Rui", 1002L);
  }

  private static Student student(String name, Long number) {
    Student student = ModelTestData.student(name);
    ReflectionTestUtils.setField(student, "number", number);
    return student;
  }

  private Group group(String assignmentName, LocalDate date, int number, Student... members) {
    Group group = draft(project(assignmentName, date), number, members);
    group.publish();
    return group;
  }

  private Assignment project(String name, LocalDate date) {
    return new Assignment(edition, name, AssignmentType.PROJECT, date, new BigDecimal("0.20"),
        new GroupingRule(2, 2, 3, false));
  }

  private static Group draft(Assignment assignment, int number, Student... members) {
    Group group = new Group(assignment, number);
    for (Student member : members) {
      group.addMember(member);
    }
    return group;
  }

  // view groups: the assignment, its groups and the class of each enrolled student
  private Assignment viewing(List<Group> groups, Student... enrolled) {
    Assignment assignment = groups.isEmpty() ? project("Project", LocalDate.of(2026, 1, 10))
        : groups.getFirst().getAssignment();
    when(assignmentService.getAssignment(1L, 2L)).thenReturn(assignment);
    when(groupRepository.findByAssignmentOrderByNumber(assignment)).thenReturn(groups);
    when(enrolmentService.listEnrolments(1L))
        .thenReturn(List.of(enrolled).stream().map(ModelTestData::enrolment).toList());
    return assignment;
  }

  @Test
  @DisplayName("Only published groups are asked for, latest assignment first, then by group number")
  void latestAssignmentFirst() {
    // Arrange
    Group earlier = group("Earlier", LocalDate.of(2026, 1, 10), 1, ana, rui);
    Group laterSecond = group("Later", LocalDate.of(2026, 3, 10), 2, ana);
    Group laterFirst = group("Later", LocalDate.of(2026, 3, 10), 1, ana);
    when(groupRepository.findWithMember(ANA, GroupStatus.PUBLISHED))
        .thenReturn(List.of(earlier, laterSecond, laterFirst));

    // Act
    List<MyGroupDTO> groups = groupService.listMyGroups(ANA);

    // Assert
    assertEquals(List.of("Later", "Later", "Earlier"), groups.stream().map(MyGroupDTO::assignmentName).toList());
    assertEquals(List.of(1, 2, 1), groups.stream().map(MyGroupDTO::number).toList());
    assertEquals("TEST", groups.getFirst().subjectCode());
    assertEquals(LocalDate.of(2026, 3, 10), groups.getFirst().assignmentDate());
  }

  @Test
  @DisplayName("Members are sorted by name, and a blocked member is still listed, marked as blocked (GR5)")
  void blockedMemberStillListed() {
    // Arrange
    rui.block("test");
    when(groupRepository.findWithMember(ANA, GroupStatus.PUBLISHED))
        .thenReturn(List.of(group("Project", LocalDate.of(2026, 1, 10), 1, rui, ana)));

    // Act
    List<MyGroupDTO.Member> members = groupService.listMyGroups(ANA).getFirst().members();

    // Assert
    assertEquals(List.of(ANA, 1002L), members.stream().map(MyGroupDTO.Member::number).toList());
    assertFalse(members.get(0).blocked());
    assertTrue(members.get(1).blocked());
  }

  @Test
  @DisplayName("A student in no published group gets an empty list")
  void noGroups() {
    // Arrange
    when(groupRepository.findWithMember(ANA, GroupStatus.PUBLISHED)).thenReturn(List.of());

    // Act + Assert
    assertTrue(groupService.listMyGroups(ANA).isEmpty());
  }

  @Test
  @DisplayName("View groups: the drafts and the published groups come back apart, each with its status")
  void listGroupsSplitsByStatus() {
    // Arrange
    Assignment assignment = project("Project", LocalDate.of(2026, 1, 10));
    Group published = draft(assignment, 1, ana, rui);
    published.publish();
    viewing(List.of(published, draft(assignment, 1, ana), draft(assignment, 2, rui)), ana, rui);

    // Act
    AssignmentGroupsDTO groups = groupService.listGroups(1L, 2L);

    // Assert
    assertEquals(List.of(1, 2), groups.draft().stream().map(GroupDTO::number).toList());
    assertTrue(groups.draft().stream().allMatch(group -> group.status() == GroupStatus.DRAFT));
    assertEquals(1, groups.published().size());
    assertEquals(GroupStatus.PUBLISHED, groups.published().getFirst().status());
    assertEquals(2, groups.published().getFirst().size());
  }

  @Test
  @DisplayName("View groups: no groups yet is two empty lists")
  void listGroupsEmpty() {
    // Arrange
    viewing(List.of());

    // Act
    AssignmentGroupsDTO groups = groupService.listGroups(1L, 2L);

    // Assert
    assertTrue(groups.draft().isEmpty());
    assertTrue(groups.published().isEmpty());
  }

  @Test
  @DisplayName("View groups: members by name with their class, statute as not applicable only, blocked still listed")
  void listGroupsMembers() {
    // Arrange
    rui.assignStatute(StatuteType.WORKING_STUDENT, LocalDate.now().plusYears(1));
    Student bia = student("Bia", 1003L);
    bia.block("test");
    viewing(List.of(draft(project("Project", LocalDate.of(2026, 1, 10)), 1, rui, bia, ana)), ana, rui, bia);

    // Act
    List<GroupDTO.Member> members = groupService.listGroups(1L, 2L).draft().getFirst().members();

    // Assert
    assertEquals(List.of("Ana", "Bia", "Rui"), members.stream().map(GroupDTO.Member::name).toList());
    assertEquals("T1", members.getFirst().classSection());
    assertTrue(members.get(0).attendanceApplicable());
    assertFalse(members.get(2).attendanceApplicable());
    assertTrue(members.get(1).blocked());
    assertFalse(members.get(0).blocked());
  }

  @Test
  @DisplayName("View groups: the languages every member speaks, none in common is an empty list (GR4)")
  void listGroupsSharedLanguages() {
    // Arrange
    ana.setLanguages(Set.of(Language.PORTUGUESE, Language.ENGLISH));
    rui.setLanguages(Set.of(Language.ENGLISH));
    Student bia = student("Bia", 1003L);
    bia.setLanguages(Set.of(Language.PORTUGUESE));
    Assignment assignment = project("Project", LocalDate.of(2026, 1, 10));
    viewing(List.of(draft(assignment, 1, ana, rui), draft(assignment, 2, rui, bia)), ana, rui, bia);

    // Act
    List<GroupDTO> groups = groupService.listGroups(1L, 2L).draft();

    // Assert
    assertEquals(List.of(Language.ENGLISH), groups.get(0).sharedLanguages());
    assertTrue(groups.get(1).sharedLanguages().isEmpty());
  }

  @Test
  @DisplayName("View groups: an assignment without a grouping rule is ASSIGNMENT_NOT_GROUP_WORK")
  void listGroupsNotGroupWork() {
    // Arrange
    when(assignmentService.getAssignment(1L, 2L)).thenReturn(new Assignment(edition, "Exam", AssignmentType.EXAM,
        LocalDate.of(2026, 1, 20), new BigDecimal("0.60"), null));

    // Act
    StudyMatchException exception = assertThrows(StudyMatchException.class, () -> groupService.listGroups(1L, 2L));

    // Assert
    assertEquals(ErrorCode.ASSIGNMENT_NOT_GROUP_WORK, exception.getCode());
  }

  // export groups: the assignment and its published groups, numbered from 1
  private void exporting(List<List<Student>> published) {
    Assignment assignment = project("Project", LocalDate.of(2026, 1, 10));
    when(assignmentService.getAssignment(1L, 2L)).thenReturn(assignment);
    List<Group> groups = new ArrayList<>();
    for (List<Student> members : published) {
      Group group = draft(assignment, groups.size() + 1, members.toArray(Student[]::new));
      group.publish();
      groups.add(group);
    }
    when(groupRepository.findWithMembersByAssignmentAndStatusOrderByNumber(assignment, GroupStatus.PUBLISHED))
        .thenReturn(groups);
  }

  @Test
  @DisplayName("Export groups as CSV: one row per student, by group and then by name, never the statute (GR7)")
  void exportCsv() {
    // Arrange
    Student bia = student("Bia", 1003L);
    for (Student student : List.of(ana, rui, bia)) {
      student.generateInstitutionalEmail("studymatch.com");
    }
    rui.assignStatute(StatuteType.WORKING_STUDENT, LocalDate.now().plusYears(1));
    exporting(List.of(List.of(rui, ana), List.of(bia)));

    // Act
    String csv = new String(groupService.exportGroups(1L, 2L, ExportFormat.CSV, "en"), StandardCharsets.UTF_8);

    // Assert
    assertEquals(List.of("﻿groupNumber,studentNumber,name,institutionalEmail",
        "1,1001,Ana,1001@students.studymatch.com",
        "1,1002,Rui,1002@students.studymatch.com",
        "2,1003,Bia,1003@students.studymatch.com"), csv.lines().toList());
    assertFalse(csv.toLowerCase().contains("statute"));
    assertFalse(csv.contains(StatuteType.WORKING_STUDENT.name()));
    verifyNoInteractions(groupPdfWriter);
  }

  @Test
  @DisplayName("Export groups as PDF: the same rows with the subject and assignment, in the language asked for")
  void exportPdf() {
    // Arrange
    exporting(List.of(List.of(ana)));
    byte[] pdf = { 1, 2, 3 };
    when(groupPdfWriter.write("Test Subject", "Project", List.of(new GroupCsvWriter.Row(1, ANA, "Ana", null)),
        Locale.forLanguageTag("pt-PT"))).thenReturn(pdf);

    // Act + Assert
    assertEquals(pdf, groupService.exportGroups(1L, 2L, ExportFormat.PDF, "pt"));
  }

  @Test
  @DisplayName("Export groups: no published groups is NO_GROUPS_TO_EXPORT, drafts don't count")
  void exportWithoutPublishedGroups() {
    // Arrange - the repository is only asked for the published ones
    exporting(List.of());

    // Act
    StudyMatchException exception = assertThrows(StudyMatchException.class,
        () -> groupService.exportGroups(1L, 2L, ExportFormat.CSV, "en"));

    // Assert
    assertEquals(ErrorCode.NO_GROUPS_TO_EXPORT, exception.getCode());
    verifyNoInteractions(groupPdfWriter);
  }

  @Test
  @DisplayName("Export groups: an assignment without a grouping rule is ASSIGNMENT_NOT_GROUP_WORK")
  void exportNotGroupWork() {
    // Arrange
    when(assignmentService.getAssignment(1L, 2L)).thenReturn(new Assignment(edition, "Exam", AssignmentType.EXAM,
        LocalDate.of(2026, 1, 20), new BigDecimal("0.60"), null));

    // Act
    StudyMatchException exception = assertThrows(StudyMatchException.class,
        () -> groupService.exportGroups(1L, 2L, ExportFormat.PDF, "en"));

    // Assert
    assertEquals(ErrorCode.ASSIGNMENT_NOT_GROUP_WORK, exception.getCode());
    verifyNoInteractions(groupPdfWriter);
  }

  @Test
  @DisplayName("Export groups: an assignment of another edition is NOT_FOUND")
  void exportUnknownAssignment() {
    // Arrange
    when(assignmentService.getAssignment(1L, 2L)).thenThrow(new StudyMatchException(ErrorCode.NOT_FOUND));

    // Act
    StudyMatchException exception = assertThrows(StudyMatchException.class,
        () -> groupService.exportGroups(1L, 2L, ExportFormat.CSV, "en"));

    // Assert
    assertEquals(ErrorCode.NOT_FOUND, exception.getCode());
  }

  // edit groups: the assignment's groups of one status (ids from 11, numbered from 1) and who is enrolled
  private List<Group> editing(GroupStatus status, List<List<Student>> groups, Student... enrolled) {
    Assignment assignment = project("Project", LocalDate.of(2026, 1, 10));
    when(assignmentService.getLockedAssignment(1L, 2L)).thenReturn(assignment);
    List<Group> current = new ArrayList<>();
    for (List<Student> members : groups) {
      Group group = draft(assignment, current.size() + 1, members.toArray(Student[]::new));
      ReflectionTestUtils.setField(group, "id", 11L + current.size());
      if (status == GroupStatus.PUBLISHED) {
        group.publish();
      }
      current.add(group);
    }
    when(groupRepository.findWithMembersByAssignmentAndStatusOrderByNumber(assignment, status)).thenReturn(current);
    when(enrolmentService.listEnrolments(1L))
        .thenReturn(List.of(enrolled).stream().map(ModelTestData::enrolment).toList());
    when(groupRepository.save(any(Group.class))).thenAnswer(invocation -> invocation.getArgument(0));
    return current;
  }

  private static EditGroupsRequestDTO.EditedGroup edited(Long id, Student... members) {
    return new EditGroupsRequestDTO.EditedGroup(id, List.of(members).stream().map(Student::getNumber).toList());
  }

  // the groups handed to deleteAll, as a list
  private List<Group> deleted() {
    @SuppressWarnings("unchecked")
    ArgumentCaptor<Iterable<Group>> captor = ArgumentCaptor.forClass(Iterable.class);
    verify(groupRepository).deleteAll(captor.capture());
    List<Group> groups = new ArrayList<>();
    captor.getValue().forEach(groups::add);
    return groups;
  }

  private StudyMatchException editFails(GroupStatus status, List<EditGroupsRequestDTO.EditedGroup> edited) {
    return assertThrows(StudyMatchException.class, () -> groupService.editGroups(1L, 2L, status, edited));
  }

  @Test
  @DisplayName("Edit groups: moving a student keeps both groups, with their ids and numbers")
  void editMovesStudent() {
    // Arrange
    Student bia = student("Bia", 1003L);
    List<Group> current = editing(GroupStatus.DRAFT, List.of(List.of(ana, rui), List.of(bia)), ana, rui, bia);

    // Act
    List<GroupDTO> groups = groupService.editGroups(1L, 2L, GroupStatus.DRAFT,
        List.of(edited(11L, ana), edited(12L, rui, bia)));

    // Assert
    assertEquals(List.of(11L, 12L), groups.stream().map(GroupDTO::id).toList());
    assertEquals(List.of(1, 2), groups.stream().map(GroupDTO::number).toList());
    assertEquals(Set.of(ana), current.get(0).getMembers());
    assertEquals(Set.of(rui, bia), current.get(1).getMembers());
    assertTrue(deleted().isEmpty());
  }

  @Test
  @DisplayName("Edit groups: a removed group is deleted and the ones after it move down a number")
  void editRemovesGroup() {
    // Arrange
    Student bia = student("Bia", 1003L);
    List<Group> current = editing(GroupStatus.DRAFT, List.of(List.of(ana), List.of(rui), List.of(bia)),
        ana, rui, bia);

    // Act
    List<GroupDTO> groups = groupService.editGroups(1L, 2L, GroupStatus.DRAFT,
        List.of(edited(11L, ana, rui), edited(13L, bia)));

    // Assert
    assertEquals(List.of(current.get(1)), deleted());
    assertEquals(List.of(11L, 13L), groups.stream().map(GroupDTO::id).toList());
    assertEquals(List.of(1, 2), groups.stream().map(GroupDTO::number).toList());
  }

  @Test
  @DisplayName("Edit groups: a new group in the published ones is published too, numbered after the kept ones")
  void editAddsPublishedGroup() {
    // Arrange
    Student bia = student("Bia", 1003L);
    editing(GroupStatus.PUBLISHED, List.of(List.of(ana, rui, bia)), ana, rui, bia);

    // Act
    List<GroupDTO> groups = groupService.editGroups(1L, 2L, GroupStatus.PUBLISHED,
        List.of(edited(null, bia), edited(11L, ana, rui)));

    // Assert - sorted by number, so the kept group comes first
    assertEquals(List.of(1, 2), groups.stream().map(GroupDTO::number).toList());
    assertEquals(11L, groups.get(0).id());
    assertEquals(List.of(GroupStatus.PUBLISHED, GroupStatus.PUBLISHED),
        groups.stream().map(GroupDTO::status).toList());
    assertEquals(List.of(1003L), groups.get(1).members().stream().map(GroupDTO.Member::number).toList());
  }

  @Test
  @DisplayName("Edit groups: an enrolled student left out is STUDENT_WITHOUT_GROUP with their number, nothing saved")
  void editLeavesStudentOut() {
    // Arrange
    editing(GroupStatus.DRAFT, List.of(List.of(ana, rui)), ana, rui);

    // Act
    StudyMatchException exception = editFails(GroupStatus.DRAFT, List.of(edited(11L, ana)));

    // Assert
    assertEquals(ErrorCode.STUDENT_WITHOUT_GROUP, exception.getCode());
    assertEquals(List.of(1002L), exception.getParams().get("numbers"));
    verify(groupRepository, never()).save(any(Group.class));
  }

  @Test
  @DisplayName("Edit groups: a blocked student can be left out (GR5)")
  void editLeavesBlockedOut() {
    // Arrange
    rui.block("test");
    editing(GroupStatus.DRAFT, List.of(List.of(ana, rui)), ana, rui);

    // Act
    List<GroupDTO> groups = groupService.editGroups(1L, 2L, GroupStatus.DRAFT, List.of(edited(11L, ana)));

    // Assert
    assertEquals(List.of(ANA), groups.getFirst().members().stream().map(GroupDTO.Member::number).toList());
  }

  @Test
  @DisplayName("Edit groups: a blocked student can stay in the group they were in (GR5)")
  void editKeepsBlockedInGroup() {
    // Arrange
    Student bia = student("Bia", 1003L);
    rui.block("test");
    List<Group> current = editing(GroupStatus.DRAFT, List.of(List.of(ana, rui), List.of(bia)), ana, rui, bia);

    // Act
    groupService.editGroups(1L, 2L, GroupStatus.DRAFT, List.of(edited(11L, rui), edited(12L, ana, bia)));

    // Assert
    assertEquals(Set.of(rui), current.get(0).getMembers());
  }

  @Test
  @DisplayName("Edit groups: a blocked student moved to another group is BLOCKED_STUDENT_MOVED (GR5)")
  void editMovesBlockedStudent() {
    // Arrange
    rui.block("test");
    editing(GroupStatus.DRAFT, List.of(List.of(ana, rui), List.of()), ana, rui);

    // Act
    StudyMatchException exception = editFails(GroupStatus.DRAFT, List.of(edited(11L, ana), edited(12L, rui)));

    // Assert
    assertEquals(ErrorCode.BLOCKED_STUDENT_MOVED, exception.getCode());
    assertEquals(1002L, exception.getParams().get("number"));
    verify(groupRepository, never()).save(any(Group.class));
  }

  @Test
  @DisplayName("Edit groups: a blocked student put in a new group is BLOCKED_STUDENT_MOVED (GR5)")
  void editPutsBlockedInNewGroup() {
    // Arrange
    rui.block("test");
    editing(GroupStatus.DRAFT, List.of(List.of(ana, rui)), ana, rui);

    // Act
    StudyMatchException exception = editFails(GroupStatus.DRAFT, List.of(edited(11L, ana), edited(null, rui)));

    // Assert
    assertEquals(ErrorCode.BLOCKED_STUDENT_MOVED, exception.getCode());
  }

  @Test
  @DisplayName("Edit groups: a blocked student who had no group can't be added to one (GR5)")
  void editAddsBlockedWithoutGroup() {
    // Arrange
    rui.block("test");
    editing(GroupStatus.DRAFT, List.of(List.of(ana)), ana, rui);

    // Act
    StudyMatchException exception = editFails(GroupStatus.DRAFT, List.of(edited(11L, ana, rui)));

    // Assert
    assertEquals(ErrorCode.BLOCKED_STUDENT_MOVED, exception.getCode());
  }

  @Test
  @DisplayName("Edit groups: a student in two groups is STUDENT_IN_TWO_GROUPS")
  void editStudentTwice() {
    // Arrange
    editing(GroupStatus.DRAFT, List.of(List.of(ana), List.of(rui)), ana, rui);

    // Act
    StudyMatchException exception = editFails(GroupStatus.DRAFT, List.of(edited(11L, ana), edited(12L, rui, ana)));

    // Assert
    assertEquals(ErrorCode.STUDENT_IN_TWO_GROUPS, exception.getCode());
    assertEquals(ANA, exception.getParams().get("number"));
  }

  @Test
  @DisplayName("Edit groups: a student not enrolled in the edition is STUDENT_NOT_ENROLLED")
  void editStudentNotEnrolled() {
    // Arrange
    editing(GroupStatus.DRAFT, List.of(List.of(ana)), ana);

    // Act
    StudyMatchException exception = editFails(GroupStatus.DRAFT, List.of(edited(11L, ana, rui)));

    // Assert
    assertEquals(ErrorCode.STUDENT_NOT_ENROLLED, exception.getCode());
    assertEquals(1002L, exception.getParams().get("number"));
  }

  @Test
  @DisplayName("Edit groups: an id that isn't one of these groups is NOT_FOUND, nothing deleted")
  void editUnknownGroup() {
    // Arrange
    editing(GroupStatus.DRAFT, List.of(List.of(ana, rui)), ana, rui);

    // Act
    StudyMatchException exception = editFails(GroupStatus.DRAFT, List.of(edited(99L, ana, rui)));

    // Assert
    assertEquals(ErrorCode.NOT_FOUND, exception.getCode());
    verify(groupRepository, never()).deleteAll(any());
  }

  @Test
  @DisplayName("Edit groups: the same group sent twice is NOT_FOUND the second time")
  void editSameGroupTwice() {
    // Arrange
    editing(GroupStatus.DRAFT, List.of(List.of(ana, rui)), ana, rui);

    // Act + Assert
    assertEquals(ErrorCode.NOT_FOUND,
        editFails(GroupStatus.DRAFT, List.of(edited(11L, ana), edited(11L, rui))).getCode());
  }

  @Test
  @DisplayName("Edit groups: no drafts is NO_DRAFT_GROUPS, no published groups is NO_PUBLISHED_GROUPS")
  void editWithoutGroups() {
    // Arrange - the published ones aren't stubbed, so there are none either
    editing(GroupStatus.DRAFT, List.of(), ana);

    // Act + Assert
    assertEquals(ErrorCode.NO_DRAFT_GROUPS, editFails(GroupStatus.DRAFT, List.of(edited(null, ana))).getCode());
    assertEquals(ErrorCode.NO_PUBLISHED_GROUPS,
        editFails(GroupStatus.PUBLISHED, List.of(edited(null, ana))).getCode());
  }

  @Test
  @DisplayName("Edit groups: an assignment without a grouping rule is ASSIGNMENT_NOT_GROUP_WORK")
  void editNotGroupWork() {
    // Arrange
    when(assignmentService.getLockedAssignment(1L, 2L)).thenReturn(new Assignment(edition, "Exam",
        AssignmentType.EXAM, LocalDate.of(2026, 1, 20), new BigDecimal("0.60"), null));

    // Act + Assert
    assertEquals(ErrorCode.ASSIGNMENT_NOT_GROUP_WORK,
        editFails(GroupStatus.DRAFT, List.of(edited(null, ana))).getCode());
  }

  // import groups: an assignment with that size range and who is enrolled
  private Assignment importing(GroupingRule rule, Student... enrolled) {
    Assignment assignment = new Assignment(edition, "Project", AssignmentType.PROJECT, LocalDate.of(2026, 1, 10),
        new BigDecimal("0.20"), rule);
    when(assignmentService.getLockedAssignment(1L, 2L)).thenReturn(assignment);
    when(enrolmentService.listEnrolments(1L))
        .thenReturn(List.of(enrolled).stream().map(ModelTestData::enrolment).toList());
    when(groupRepository.save(any(Group.class))).thenAnswer(invocation -> invocation.getArgument(0));
    return assignment;
  }

  // pairs of group and student number, the rows start at 2 like a spreadsheet's under its header
  private static List<GroupCsvReader.Row> rows(Object... values) {
    List<GroupCsvReader.Row> rows = new ArrayList<>();
    for (int i = 0; i < values.length; i += 2) {
      rows.add(new GroupCsvReader.Row(rows.size() + 2, (Integer) values[i], (Long) values[i + 1]));
    }
    return rows;
  }

  // every number in the file exists, unless the test says otherwise
  private GroupImportDTO importAll(List<GroupCsvReader.Row> rows) {
    Set<Long> known = rows.stream().map(GroupCsvReader.Row::studentNumber).collect(Collectors.toSet());
    return groupService.importGroups(1L, 2L, rows, known, false);
  }

  private static List<Set<Long>> members(GroupImportDTO preview) {
    return preview.groups().stream()
        .map(group -> group.group().members().stream().map(GroupDTO.Member::number).collect(Collectors.toSet()))
        .toList();
  }

  private static List<GroupImportDTO.Origin> origins(GroupImportDTO preview) {
    return preview.groups().stream().map(GroupImportDTO.ImportedGroup::origin).toList();
  }

  @Test
  @DisplayName("Import groups: the file's groups keep their order numbered from 1, the students left out form a "
      + "leftover group after them")
  void importKeepsGroupsAndGroupsLeftovers() {
    // Arrange
    Student bia = student("Bia", 1003L);
    Student caio = student("Caio", 1004L);
    Student dina = student("Dina", 1005L);
    Student edu = student("Edu", 1006L);
    Assignment assignment = importing(new GroupingRule(2, 2, 3, false), ana, rui, bia, caio, dina, edu);

    // Act - groups 7 and 3 in the file, Dina and Edu aren't in it
    GroupImportDTO preview = importAll(rows(7, 1003L, 3, ANA, 7, 1004L, 3, 1002L));

    // Assert
    assertEquals(List.of(1, 2, 3), preview.groups().stream().map(group -> group.group().number()).toList());
    assertEquals(List.of(Set.of(ANA, 1002L), Set.of(1003L, 1004L), Set.of(1005L, 1006L)), members(preview));
    assertEquals(List.of(GroupImportDTO.Origin.IMPORTED, GroupImportDTO.Origin.IMPORTED,
        GroupImportDTO.Origin.LEFTOVER), origins(preview));
    assertTrue(preview.groups().stream().allMatch(group -> group.group().status() == GroupStatus.DRAFT));
    assertEquals(4, preview.imported());
    assertEquals(2, preview.leftover());
    assertTrue(preview.skipped().isEmpty());
    assertTrue(preview.placed().isEmpty());
    verify(groupRepository).deleteByAssignmentAndStatus(assignment, GroupStatus.DRAFT);
    verify(groupRepository, never()).deleteByAssignmentAndStatus(assignment, GroupStatus.PUBLISHED);
  }

  @Test
  @DisplayName("Import groups: students not enrolled or blocked are skipped with their row and reason, and a "
      + "blocked student isn't put in a leftover group either (GR5)")
  void importSkipsNotEnrolledAndBlocked() {
    // Arrange
    Student bia = student("Bia", 1003L);
    bia.block("test");
    importing(new GroupingRule(2, 2, 3, false), ana, rui, bia);

    // Act
    GroupImportDTO preview = importAll(rows(1, ANA, 1, 9999L, 1, 1002L, 2, 1003L));

    // Assert
    assertEquals(List.of(new GroupImportDTO.Problem(3, 9999L, GroupImportDTO.Reason.NOT_ENROLLED),
        new GroupImportDTO.Problem(5, 1003L, GroupImportDTO.Reason.BLOCKED)), preview.skipped());
    assertEquals(List.of(Set.of(ANA, 1002L)), members(preview));
    assertEquals(0, preview.leftover());
  }

  @Test
  @DisplayName("Import groups: a group left with one student is dissolved and the student joins the leftover ones")
  void importDissolvesGroupOfOne() {
    // Arrange - Caio's partner isn't enrolled, Bia isn't in the file
    Student bia = student("Bia", 1003L);
    Student caio = student("Caio", 1004L);
    importing(new GroupingRule(2, 2, 3, false), ana, rui, bia, caio);

    // Act
    GroupImportDTO preview = importAll(rows(1, ANA, 1, 1002L, 2, 1004L, 2, 9999L));

    // Assert
    assertEquals(List.of(Set.of(ANA, 1002L), Set.of(1003L, 1004L)), members(preview));
    assertEquals(List.of(GroupImportDTO.Origin.IMPORTED, GroupImportDTO.Origin.LEFTOVER), origins(preview));
    assertEquals(2, preview.imported());
    assertEquals(2, preview.leftover());
  }

  @Test
  @DisplayName("Import groups: too few leftover students for a group go into the smallest imported group, "
      + "and are marked as placed")
  void importPlacesTooFewLeftovers() {
    // Arrange - groups of 3 to 4, one group of 4 and one of 3 in the file, Gil left out
    Student bia = student("Bia", 1003L);
    Student caio = student("Caio", 1004L);
    Student dina = student("Dina", 1005L);
    Student edu = student("Edu", 1006L);
    Student gil = student("Gil", 1007L);
    Student ivo = student("Ivo", 1008L);
    importing(new GroupingRule(3, 3, 4, false), ana, rui, bia, caio, dina, edu, gil, ivo);

    // Act
    GroupImportDTO preview = importAll(rows(1, ANA, 1, 1002L, 1, 1003L, 1, 1008L, 2, 1004L, 2, 1005L, 2, 1006L));

    // Assert
    assertEquals(List.of(Set.of(ANA, 1002L, 1003L, 1008L), Set.of(1004L, 1005L, 1006L, 1007L)), members(preview));
    assertEquals(List.of(1007L), preview.placed());
    assertEquals(1, preview.leftover());
    assertEquals(List.of(GroupImportDTO.Origin.IMPORTED, GroupImportDTO.Origin.IMPORTED), origins(preview));
  }

  @Test
  @DisplayName("Import groups: leftovers that don't split form as many groups as the sizes allow, the rest is placed")
  void importSplitsWhatItCan() {
    // Arrange - groups of exactly 2, three students left out
    Student bia = student("Bia", 1003L);
    Student caio = student("Caio", 1004L);
    Student dina = student("Dina", 1005L);
    importing(new GroupingRule(2, 2, 2, false), ana, rui, bia, caio, dina);

    // Act
    GroupImportDTO preview = importAll(rows(1, ANA, 1, 1002L));

    // Assert - Bia and Caio form a group, Dina goes into the imported one
    assertEquals(List.of(Set.of(ANA, 1002L, 1005L), Set.of(1003L, 1004L)), members(preview));
    assertEquals(List.of(1005L), preview.placed());
    assertEquals(3, preview.leftover());
  }

  @Test
  @DisplayName("Import groups: every invalid row is listed with its line and reason, and nothing is saved")
  void importInvalidRows() {
    // Arrange - 1003 doesn't exist, 1002 is in two groups, the third row has no group number
    importing(new GroupingRule(2, 2, 3, false), ana, rui);
    List<GroupCsvReader.Row> rows = rows(1, ANA, 1, 1002L, null, ANA, 2, 1003L, 2, 1002L);

    // Act
    StudyMatchException e = assertThrows(StudyMatchException.class,
        () -> groupService.importGroups(1L, 2L, rows, Set.of(ANA, 1002L), false));

    // Assert
    assertEquals(ErrorCode.GROUP_IMPORT_INVALID_ROWS, e.getCode());
    assertEquals(Map.of("rows", List.of(new GroupImportDTO.Problem(4, ANA, GroupImportDTO.Reason.INVALID),
        new GroupImportDTO.Problem(5, 1003L, GroupImportDTO.Reason.UNKNOWN_STUDENT),
        new GroupImportDTO.Problem(6, 1002L, GroupImportDTO.Reason.IN_TWO_GROUPS))), e.getParams());
    verify(groupRepository, never()).save(any());
    verify(groupRepository, never()).deleteByAssignmentAndStatus(any(), any());
  }

  @Test
  @DisplayName("Import groups: the same student twice in the same group counts once")
  void importSameGroupTwice() {
    // Arrange
    importing(new GroupingRule(2, 2, 3, false), ana, rui);

    // Act
    GroupImportDTO preview = importAll(rows(1, ANA, 1, 1002L, 1, ANA));

    // Assert
    assertEquals(List.of(Set.of(ANA, 1002L)), members(preview));
    assertEquals(2, preview.imported());
  }

  @Test
  @DisplayName("Import groups: a file with nobody enrolled here is GROUP_FILE_NOT_THIS_EDITION")
  void importNobodyEnrolled() {
    // Arrange
    importing(new GroupingRule(2, 2, 3, false), ana, rui);

    // Act
    StudyMatchException e = assertThrows(StudyMatchException.class, () -> importAll(rows(1, 9998L, 1, 9999L)));

    // Assert
    assertEquals(ErrorCode.GROUP_FILE_NOT_THIS_EDITION, e.getCode());
    verify(groupRepository, never()).save(any());
  }

  @Test
  @DisplayName("Import groups: with published groups it's GROUPS_ALREADY_PUBLISHED unless replacing is confirmed, "
      + "and even then the published ones stay until publishing")
  void importOverPublished() {
    // Arrange
    Assignment assignment = importing(new GroupingRule(2, 2, 3, false), ana, rui);
    when(groupRepository.existsByAssignmentAndStatus(assignment, GroupStatus.PUBLISHED)).thenReturn(true);
    List<GroupCsvReader.Row> rows = rows(1, ANA, 1, 1002L);

    // Act
    StudyMatchException e = assertThrows(StudyMatchException.class, () -> importAll(rows));
    GroupImportDTO preview = groupService.importGroups(1L, 2L, rows, Set.of(ANA, 1002L), true);

    // Assert
    assertEquals(ErrorCode.GROUPS_ALREADY_PUBLISHED, e.getCode());
    assertEquals(1, preview.groups().size());
    verify(groupRepository, never()).deleteByAssignmentAndStatus(assignment, GroupStatus.PUBLISHED);
  }

  @Test
  @DisplayName("Import groups: with no group from the file and too few students left, it's "
      + "NOT_ENOUGH_STUDENTS_FOR_GROUPS")
  void importNothingToGroup() {
    // Arrange - groups of 3 to 4, both groups in the file are of one
    importing(new GroupingRule(3, 3, 4, false), ana, rui);

    // Act
    StudyMatchException e = assertThrows(StudyMatchException.class, () -> importAll(rows(1, ANA, 2, 1002L)));

    // Assert
    assertEquals(ErrorCode.NOT_ENOUGH_STUDENTS_FOR_GROUPS, e.getCode());
    verify(groupRepository, never()).save(any());
  }

  @Test
  @DisplayName("Import groups: an assignment without a grouping rule is ASSIGNMENT_NOT_GROUP_WORK")
  void importNotGroupWork() {
    // Arrange
    importing(null, ana, rui);

    // Act
    StudyMatchException e = assertThrows(StudyMatchException.class, () -> importAll(rows(1, ANA, 1, 1002L)));

    // Assert
    assertEquals(ErrorCode.ASSIGNMENT_NOT_GROUP_WORK, e.getCode());
  }

}
