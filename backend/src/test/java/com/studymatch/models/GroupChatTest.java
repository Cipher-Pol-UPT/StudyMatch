package com.studymatch.models;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.studymatch.enums.AssignmentType;

class GroupChatTest {

  private Group group;
  private Student ana;
  private Student rui;
  private GroupChat chat;

  @BeforeEach
  void setUp() {
    Assignment project = new Assignment(ModelTestData.subjectEdition(), "Test Project", AssignmentType.PROJECT,
        LocalDate.now(), new BigDecimal("0.50"), new GroupingRule(2, 3, 4, false));
    group = new Group(project, 3);
    ana = student("Ana", 1001L);
    rui = student("Rui", 1002L);
    group.addMember(ana);
    chat = new GroupChat(group);
  }

  // numbers are given by the database, so they're set by hand here
  private static Student student(String name, Long number) {
    Student student = ModelTestData.student(name);
    ReflectionTestUtils.setField(student, "number", number);
    return student;
  }

  @Test
  @DisplayName("The group's students are the members, anyone else isn't")
  void membersAreTheGroup() {
    // Act + Assert
    assertTrue(chat.hasMember(ana));
    assertFalse(chat.hasMember(rui));
    assertFalse(chat.hasMember(ModelTestData.teacher()));
    assertEquals(List.of(ana), chat.getMembers());
  }

  @Test
  @DisplayName("Moving a student in the group changes who's in the chat, nothing else to update (GR8)")
  void membersFollowTheGroup() {
    // Act
    group.replaceMembers(List.of(rui));

    // Assert
    assertFalse(chat.hasMember(ana));
    assertTrue(chat.hasMember(rui));
    assertEquals(Set.of(rui), Set.copyOf(chat.getMembers()));
  }

  @Test
  @DisplayName("Membership goes by number, so another copy of the same student is still a member")
  void membershipByNumber() {
    // Act + Assert
    assertTrue(chat.hasMember(student("Ana", 1001L)));
  }

  @Test
  @DisplayName("It's named after the assignment, the subject and the group number, the same for everyone")
  void nameFromAssignment() {
    // Act + Assert
    assertEquals("Test Project · Test Subject · Group 3", chat.getNameFor(ana));
    assertEquals(chat.getNameFor(ana), chat.getNameFor(rui));
  }

}
