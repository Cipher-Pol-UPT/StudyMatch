package com.studymatch.models;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.stream.LongStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.studymatch.exceptions.ErrorCode;
import com.studymatch.exceptions.StudyMatchException;

class CustomChatTest {

  private Student ana;
  private Student rui;
  private Student eva;

  @BeforeEach
  void setUp() {
    ana = student("Ana", 1001L);
    rui = student("Rui", 1002L);
    eva = student("Eva", 1003L);
  }

  // numbers are given by the database, so they're set by hand here
  private static Student student(String name, Long number) {
    Student student = ModelTestData.student(name);
    ReflectionTestUtils.setField(student, "number", number);
    return student;
  }

  // count people besides the owner, numbers from 2001 up
  private static List<User> others(int count) {
    return LongStream.range(0, count).mapToObj(i -> (User) student("Member " + i, 2001 + i)).toList();
  }

  private static String png(int bytes) {
    byte[] image = new byte[bytes];
    image[0] = (byte) 0x89;
    image[1] = 0x50;
    image[2] = 0x4E;
    image[3] = 0x47;
    return "data:image/png;base64," + Base64.getEncoder().encodeToString(image);
  }

  @Test
  @DisplayName("The owner is the first member, then the others in the order they were picked")
  void ownerFirstThenOthers() {
    // Act
    CustomChat chat = new CustomChat("Study group", null, ana, List.of(rui, eva));

    // Assert
    assertSame(ana, chat.getOwner());
    assertEquals(List.of(ana, rui, eva), chat.getMembers());
  }

  @Test
  @DisplayName("The owner and 2 others is the smallest group chat (GR8)")
  void threeMembersIsEnough() {
    // Act + Assert
    assertDoesNotThrow(() -> new CustomChat("Study group", null, ana, List.of(rui, eva)));
  }

  @Test
  @DisplayName("Only 1 other person is refused, that's a DM (GR8)")
  void twoMembersIsRefused() {
    // Act
    StudyMatchException e = assertThrows(StudyMatchException.class,
        () -> new CustomChat("Study group", null, ana, List.of(rui)));

    // Assert
    assertEquals(ErrorCode.NOT_ENOUGH_GROUP_CHAT_MEMBERS, e.getCode());
  }

  @Test
  @DisplayName("20 members counting the owner is allowed (GR8)")
  void twentyMembersIsAllowed() {
    // Act + Assert
    assertDoesNotThrow(() -> new CustomChat("Study group", null, ana, others(CustomChat.MAX_MEMBERS - 1)));
  }

  @Test
  @DisplayName("21 members counting the owner is refused (GR8)")
  void twentyOneMembersIsRefused() {
    // Act
    StudyMatchException e = assertThrows(StudyMatchException.class,
        () -> new CustomChat("Study group", null, ana, others(CustomChat.MAX_MEMBERS)));

    // Assert
    assertEquals(ErrorCode.TOO_MANY_GROUP_CHAT_MEMBERS, e.getCode());
  }

  @Test
  @DisplayName("A PNG picture up to 1 MB is saved")
  void pictureIsSaved() {
    // Arrange
    String picture = png(CustomChat.MAX_PICTURE_BYTES);

    // Act
    CustomChat chat = new CustomChat("Study group", picture, ana, List.of(rui, eva));

    // Assert
    assertEquals(picture, chat.getPicture());
    assertNotNull(chat.getPictureUpdatedAt());
  }

  @Test
  @DisplayName("No picture is fine")
  void noPicture() {
    // Act
    CustomChat chat = new CustomChat("Study group", null, ana, List.of(rui, eva));

    // Assert
    assertNull(chat.getPicture());
    assertNull(chat.getPictureUpdatedAt());
  }

  @Test
  @DisplayName("A new picture gets a new date, renaming keeps it and taking the picture off clears it")
  void pictureDate() {
    // Arrange
    CustomChat chat = new CustomChat("Study group", png(100), ana, List.of(rui, eva));
    LocalDateTime before = LocalDateTime.of(2026, 1, 1, 0, 0);
    ReflectionTestUtils.setField(chat, "pictureUpdatedAt", before);

    // Act + Assert
    chat.rename("Exam prep");
    assertEquals(before, chat.getPictureUpdatedAt());
    chat.setPicture(png(200));
    assertTrue(chat.getPictureUpdatedAt().isAfter(before));
    chat.setPicture(null);
    assertNull(chat.getPictureUpdatedAt());
  }

  @Test
  @DisplayName("A picture over 1 MB is refused")
  void bigPictureIsRefused() {
    // Arrange
    String picture = png(CustomChat.MAX_PICTURE_BYTES + 1);

    // Act
    StudyMatchException e = assertThrows(StudyMatchException.class,
        () -> new CustomChat("Study group", picture, ana, List.of(rui, eva)));

    // Assert
    assertEquals(ErrorCode.INVALID_GROUP_CHAT_PICTURE, e.getCode());
  }

  @Test
  @DisplayName("A picture that isn't a PNG or JPEG is refused")
  void notAnImageIsRefused() {
    // Arrange
    String gif = "data:image/gif;base64," + Base64.getEncoder().encodeToString(new byte[16]);

    // Act
    StudyMatchException e = assertThrows(StudyMatchException.class,
        () -> new CustomChat("Study group", gif, ana, List.of(rui, eva)));

    // Assert
    assertEquals(ErrorCode.INVALID_GROUP_CHAT_PICTURE, e.getCode());
  }

  @Test
  @DisplayName("Membership goes by number, someone who wasn't added isn't a member")
  void membershipByNumber() {
    // Arrange - the user loaded again in another request is a different object
    CustomChat chat = new CustomChat("Study group", null, ana, List.of(rui, eva));
    Student ruiAgain = student("Rui", 1002L);

    // Act + Assert
    assertTrue(chat.hasMember(ruiAgain));
    assertFalse(chat.hasMember(student("Zé", 1004L)));
  }

  @Test
  @DisplayName("Added people go after the current members, in the order they were picked")
  void addedGoLast() {
    // Arrange
    CustomChat chat = new CustomChat("Study group", null, ana, List.of(rui, eva));
    Student zeca = student("Zeca", 1004L);
    Student lia = student("Lia", 1005L);

    // Act
    List<User> added = chat.addMembers(List.of(zeca, lia));

    // Assert
    assertEquals(List.of(zeca, lia), added);
    assertEquals(List.of(ana, rui, eva, zeca, lia), chat.getMembers());
  }

  @Test
  @DisplayName("Someone already in isn't added again, and isn't in what comes back")
  void currentMembersSkipped() {
    // Arrange
    CustomChat chat = new CustomChat("Study group", null, ana, List.of(rui, eva));
    Student zeca = student("Zeca", 1004L);

    // Act
    List<User> added = chat.addMembers(List.of(student("Rui", 1002L), zeca));

    // Assert
    assertEquals(List.of(zeca), added);
    assertEquals(4, chat.getMembers().size());
  }

  @Test
  @DisplayName("Adding up to 20 members is allowed (GR8)")
  void addUpToTwenty() {
    // Arrange
    CustomChat chat = new CustomChat("Study group", null, ana, List.of(rui, eva));

    // Act
    chat.addMembers(others(CustomChat.MAX_MEMBERS - 3));

    // Assert
    assertEquals(CustomChat.MAX_MEMBERS, chat.getMembers().size());
  }

  @Test
  @DisplayName("Adding past 20 members is refused and nobody is added (GR8)")
  void addPastTwentyRefused() {
    // Arrange
    CustomChat chat = new CustomChat("Study group", null, ana, List.of(rui, eva));
    List<User> tooMany = others(CustomChat.MAX_MEMBERS - 2);

    // Act
    StudyMatchException e = assertThrows(StudyMatchException.class, () -> chat.addMembers(tooMany));

    // Assert
    assertEquals(ErrorCode.TOO_MANY_GROUP_CHAT_MEMBERS, e.getCode());
    assertEquals(3, chat.getMembers().size());
  }

  @Test
  @DisplayName("A removed member is no longer in it, even if the user came from another request")
  void removedMemberIsOut() {
    // Arrange
    CustomChat chat = new CustomChat("Study group", null, ana, List.of(rui, eva));

    // Act
    chat.removeMember(student("Rui", 1002L));

    // Assert
    assertFalse(chat.hasMember(rui));
    assertEquals(List.of(ana, eva), chat.getMembers());
  }

  @Test
  @DisplayName("Removing someone who isn't in it changes nothing")
  void removingAStrangerChangesNothing() {
    // Arrange
    CustomChat chat = new CustomChat("Study group", null, ana, List.of(rui, eva));

    // Act
    chat.removeMember(student("Zeca", 1004L));

    // Assert
    assertEquals(List.of(ana, rui, eva), chat.getMembers());
  }

  @Test
  @DisplayName("Someone removed can be added again, and goes at the end")
  void removedCanComeBack() {
    // Arrange
    CustomChat chat = new CustomChat("Study group", null, ana, List.of(rui, eva));
    chat.removeMember(rui);

    // Act
    chat.addMembers(List.of(rui));

    // Assert
    assertEquals(List.of(ana, eva, rui), chat.getMembers());
  }

  @Test
  @DisplayName("A member leaving is out, the owner stays")
  void memberLeaves() {
    // Arrange
    CustomChat chat = new CustomChat("Study group", null, ana, List.of(rui, eva));

    // Act
    chat.leave(rui);

    // Assert
    assertEquals(List.of(ana, eva), chat.getMembers());
    assertSame(ana, chat.getOwner());
  }

  @Test
  @DisplayName("The owner leaving makes whoever's been there longest the owner, who's then first")
  void ownerHandsOver() {
    // Arrange - zeca joined after rui and eva
    CustomChat chat = new CustomChat("Study group", null, ana, List.of(rui, eva));
    Student zeca = student("Zeca", 1004L);
    chat.addMembers(List.of(zeca));

    // Act
    chat.leave(student("Ana", 1001L));

    // Assert
    assertSame(rui, chat.getOwner());
    assertSame(chat.getOwner(), chat.getMembers().getFirst());
  }

  @Test
  @DisplayName("The new owner leaving hands it on again, by joining order")
  void handsOverTwice() {
    // Arrange
    CustomChat chat = new CustomChat("Study group", null, ana, List.of(rui, eva));
    chat.leave(ana);

    // Act
    chat.leave(rui);

    // Assert
    assertSame(eva, chat.getOwner());
    assertEquals(List.of(eva), chat.getMembers());
  }

  @Test
  @DisplayName("The last member leaving leaves nobody")
  void lastMemberLeaves() {
    // Arrange
    CustomChat chat = new CustomChat("Study group", null, ana, List.of(rui, eva));
    chat.leave(rui);
    chat.leave(eva);

    // Act
    chat.leave(ana);

    // Assert
    assertTrue(chat.getMembers().isEmpty());
  }

  @Test
  @DisplayName("A chat down to its owner can take one person and stay below 3, the 3 is only for creating")
  void smallChatTakesOneMore() {
    // Arrange - down to ana
    CustomChat chat = new CustomChat("Study group", null, ana, List.of(rui, eva));
    chat.removeMember(rui);
    chat.removeMember(eva);
    Student zeca = student("Zeca", 1004L);

    // Act
    List<User> added = chat.addMembers(List.of(zeca));

    // Assert
    assertEquals(List.of(zeca), added);
    assertEquals(2, chat.getMembers().size());
  }

  @Test
  @DisplayName("Only the owner is the owner, compared by number")
  void ownerByNumber() {
    // Arrange
    CustomChat chat = new CustomChat("Study group", null, ana, List.of(rui, eva));

    // Act + Assert
    assertTrue(chat.isOwner(student("Ana", 1001L)));
    assertFalse(chat.isOwner(rui));
  }

  @Test
  @DisplayName("Everyone sees the group chat's own name")
  void nameIsTheSameForEveryone() {
    // Arrange
    CustomChat chat = new CustomChat("Study group", null, ana, List.of(rui, eva));

    // Act + Assert
    assertEquals("Study group", chat.getNameFor(ana));
    assertEquals("Study group", chat.getNameFor(eva));
  }

}
