package com.studymatch.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.studymatch.dtos.MyGroupDTO;
import com.studymatch.enums.AssignmentType;
import com.studymatch.enums.GroupStatus;
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
  private GroupService groupService;
  private SubjectEdition edition;
  private Student ana;
  private Student rui;

  @BeforeEach
  void setUp() {
    groupRepository = mock(GroupRepository.class);
    groupService = new GroupService(groupRepository, mock(AssignmentService.class), mock(EnrolmentService.class),
        mock(AcademicYearService.class), mock(SubjectEditionService.class), mock(LessonService.class),
        mock(AbsenceService.class), mock(GroupGenerator.class));
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
    Assignment assignment = new Assignment(edition, assignmentName, AssignmentType.PROJECT, date,
        new BigDecimal("0.20"), new GroupingRule(2, 2, 3, false));
    Group group = new Group(assignment, number);
    for (Student member : members) {
      group.addMember(member);
    }
    group.publish();
    return group;
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

}
