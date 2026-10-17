package com.studymatch.models;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.studymatch.enums.AssignmentType;
import com.studymatch.enums.GroupStatus;
import com.studymatch.exceptions.ErrorCode;
import com.studymatch.exceptions.StudyMatchException;

class GroupTest {

  private SubjectEdition edition;
  private Group group;
  private Student ana;
  private Student bruno;

  @BeforeEach
  void setUp() {
    edition = ModelTestData.subjectEdition();
    Assignment groupProject = new Assignment(edition, "Test Project", AssignmentType.PROJECT, LocalDate.now(),
        new BigDecimal("0.50"), new GroupingRule(2, 3, 4, false));
    group = new Group(groupProject, 1);
    ana = ModelTestData.student("Ana Test");
    bruno = ModelTestData.student("Bruno Test");
  }

  @Test
  @DisplayName("A new group is a draft with its number and no members")
  void newGroupIsDraft() {
    assertEquals(GroupStatus.DRAFT, group.getStatus());
    assertEquals(1, group.getNumber());
    assertTrue(group.getMembers().isEmpty());
  }

  @Test
  @DisplayName("Groups can only be made for group-work assignments")
  void cannotCreateGroupForIndividualAssignment() {
    // Arrange - no grouping rule = individual work
    Assignment individual = new Assignment(edition, "Test Exam", AssignmentType.EXAM, LocalDate.now(),
        new BigDecimal("0.50"), null);

    // Act
    StudyMatchException e = assertThrows(StudyMatchException.class, () -> new Group(individual, 1));

    // Assert
    assertEquals(ErrorCode.ASSIGNMENT_NOT_GROUP_WORK, e.getCode());
  }

  @Test
  @DisplayName("Publishing a group makes it published")
  void publishGroup() {
    // Act
    group.publish();

    // Assert
    assertEquals(GroupStatus.PUBLISHED, group.getStatus());
  }

  @Test
  @DisplayName("Members can be added and removed")
  void addAndRemoveMembers() {
    // Arrange
    group.addMember(ana);
    group.addMember(bruno);

    // Act
    group.removeMember(ana);

    // Assert
    assertEquals(Set.of(bruno), group.getMembers());
  }

  @Test
  @DisplayName("Adding the same student twice keeps them once")
  void addingSameMemberTwiceKeepsOne() {
    // Act
    group.addMember(ana);
    group.addMember(ana);

    // Assert
    assertEquals(1, group.getMembers().size());
  }

  @Test
  @DisplayName("Members can't be changed directly")
  void membersCannotBeModifiedDirectly() {
    assertThrows(UnsupportedOperationException.class, () -> group.getMembers().add(ana));
  }

}
