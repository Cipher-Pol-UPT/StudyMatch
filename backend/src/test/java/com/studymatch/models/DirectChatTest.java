package com.studymatch.models;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class DirectChatTest {

  private Student ana;
  private Student rui;

  @BeforeEach
  void setUp() {
    ana = student("Ana", 1001L);
    rui = student("Rui", 1002L);
  }

  // numbers are given by the database, so they're set by hand here
  private static Student student(String name, Long number) {
    Student student = ModelTestData.student(name);
    ReflectionTestUtils.setField(student, "number", number);
    return student;
  }

  @Test
  @DisplayName("Whoever starts it, the smaller number goes first, so a pair is always the same row")
  void pairIsOrderedByNumber() {
    // Act
    DirectChat fromAna = new DirectChat(ana, rui);
    DirectChat fromRui = new DirectChat(rui, ana);

    // Assert
    assertSame(ana, fromAna.getFirstUser());
    assertSame(rui, fromAna.getSecondUser());
    assertSame(ana, fromRui.getFirstUser());
    assertSame(rui, fromRui.getSecondUser());
  }

  @Test
  @DisplayName("Both users are members, anyone else isn't")
  void membersAreTheTwoUsers() {
    // Arrange
    DirectChat chat = new DirectChat(ana, rui);

    // Act + Assert
    assertTrue(chat.hasMember(ana));
    assertTrue(chat.hasMember(rui));
    assertFalse(chat.hasMember(student("Eva", 1003L)));
  }

  @Test
  @DisplayName("Membership goes by number, so another copy of the same user is still a member")
  void membershipByNumber() {
    // Arrange - the user loaded again in another request is a different object
    DirectChat chat = new DirectChat(ana, rui);
    Student anaAgain = student("Ana", 1001L);

    // Act + Assert
    assertTrue(chat.hasMember(anaAgain));
  }

  @Test
  @DisplayName("The members are exactly the two users")
  void membersList() {
    // Arrange
    DirectChat chat = new DirectChat(rui, ana);

    // Act + Assert
    assertEquals(List.of(ana, rui), chat.getMembers());
  }

  @Test
  @DisplayName("The other user is the one who isn't asking")
  void otherUser() {
    // Arrange
    DirectChat chat = new DirectChat(ana, rui);

    // Act + Assert
    assertSame(rui, chat.getOtherUser(ana));
    assertSame(ana, chat.getOtherUser(rui));
  }

  @Test
  @DisplayName("A DM is named after the other person, so each side sees a different name")
  void nameIsTheOtherPerson() {
    // Arrange
    DirectChat chat = new DirectChat(ana, rui);

    // Act + Assert
    assertEquals("Rui", chat.getNameFor(ana));
    assertEquals("Ana", chat.getNameFor(rui));
  }

}
