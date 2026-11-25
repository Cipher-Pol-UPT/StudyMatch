package com.studymatch.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.studymatch.dtos.AssignmentGroupsDTO;
import com.studymatch.dtos.GroupDTO;
import com.studymatch.dtos.MyGroupDTO;
import com.studymatch.enums.AssignmentType;
import com.studymatch.enums.GroupStatus;
import com.studymatch.enums.Language;
import com.studymatch.enums.NotificationTarget;
import com.studymatch.enums.NotificationType;
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

class GroupServiceTest {

  private static final Long ANA = 1001L;

  private GroupRepository groupRepository;
  private AssignmentService assignmentService;
  private EnrolmentService enrolmentService;
  private NotificationService notificationService;
  private GroupService groupService;
  private SubjectEdition edition;
  private Student ana;
  private Student rui;

  @BeforeEach
  void setUp() {
    groupRepository = mock(GroupRepository.class);
    assignmentService = mock(AssignmentService.class);
    enrolmentService = mock(EnrolmentService.class);
    notificationService = mock(NotificationService.class);
    groupService = new GroupService(groupRepository, assignmentService, enrolmentService,
        mock(AcademicYearService.class), mock(SubjectEditionService.class), mock(LessonService.class),
        mock(AbsenceService.class), mock(GroupGenerator.class), notificationService);
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

  @Test
  @DisplayName("Publishing tells each member their group, pointing at it")
  void publishNotifiesMembers() {
    // Arrange
    Assignment assignment = project("Project", LocalDate.of(2026, 1, 10));
    Group first = draft(assignment, 1, ana);
    Group second = draft(assignment, 2, rui);
    ReflectionTestUtils.setField(first, "id", 11L);
    ReflectionTestUtils.setField(second, "id", 12L);
    when(assignmentService.getLockedAssignment(1L, 2L)).thenReturn(assignment);
    when(groupRepository.findByAssignmentAndStatusOrderByNumber(assignment, GroupStatus.DRAFT))
        .thenReturn(List.of(first, second));

    // Act
    groupService.publishGroups(1L, 2L);

    // Assert
    verify(notificationService).notifyAll(Set.of(ana), NotificationType.GROUP_PUBLISHED, NotificationTarget.GROUP,
        11L, Map.of("subject", "Test Subject", "assignment", "Project", "number", 1));
    verify(notificationService).notifyAll(Set.of(rui), NotificationType.GROUP_PUBLISHED, NotificationTarget.GROUP,
        12L, Map.of("subject", "Test Subject", "assignment", "Project", "number", 2));
  }

  @Test
  @DisplayName("Publishing without drafts is NO_DRAFT_GROUPS and tells nobody")
  void publishWithoutDraftsNotifiesNobody() {
    // Arrange
    Assignment assignment = project("Project", LocalDate.of(2026, 1, 10));
    when(assignmentService.getLockedAssignment(1L, 2L)).thenReturn(assignment);
    when(groupRepository.findByAssignmentAndStatusOrderByNumber(assignment, GroupStatus.DRAFT)).thenReturn(List.of());

    // Act
    StudyMatchException exception = assertThrows(StudyMatchException.class, () -> groupService.publishGroups(1L, 2L));

    // Assert
    assertEquals(ErrorCode.NO_DRAFT_GROUPS, exception.getCode());
    verifyNoInteractions(notificationService);
  }

}
