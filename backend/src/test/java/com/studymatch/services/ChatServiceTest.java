package com.studymatch.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.LongStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import com.studymatch.dtos.AddMembersRequestDTO;
import com.studymatch.dtos.ChatEditedDTO;
import com.studymatch.dtos.CreateGroupChatRequestDTO;
import com.studymatch.dtos.GroupChatPictureRequestDTO;
import com.studymatch.dtos.RenameGroupChatRequestDTO;
import com.studymatch.enums.ChatEventType;
import com.studymatch.events.ChatEvent;
import com.studymatch.events.MemberChatEvent;
import com.studymatch.events.UserChatEvent;
import com.studymatch.exceptions.ErrorCode;
import com.studymatch.exceptions.StudyMatchException;
import com.studymatch.models.CustomChat;
import com.studymatch.models.DirectChat;
import com.studymatch.models.ModelTestData;
import com.studymatch.models.Student;
import com.studymatch.models.User;
import com.studymatch.repositories.ChatRepository;
import com.studymatch.utils.PresenceTracker;

class ChatServiceTest {

  // the 8 bytes every PNG starts with
  private static final String PICTURE = "data:image/png;base64,iVBORw0KGgo=";

  private ChatRepository chatRepository;
  private UserService userService;
  private UserBlockService userBlockService;
  private ChatSettingsService chatSettingsService;
  private PresenceTracker presenceTracker;
  private ApplicationEventPublisher eventPublisher;
  private ChatService chatService;

  private Student ana;
  private DirectChat chat;

  @BeforeEach
  void setUp() {
    chatRepository = mock(ChatRepository.class);
    userService = mock(UserService.class);
    userBlockService = mock(UserBlockService.class);
    presenceTracker = new PresenceTracker();
    eventPublisher = mock(ApplicationEventPublisher.class);
    chatSettingsService = mock(ChatSettingsService.class);
    chatService = new ChatService(chatRepository, userService, userBlockService, chatSettingsService, presenceTracker,
        eventPublisher);

    ana = student("Ana", 1001L);
    chat = new DirectChat(ana, student("Rui", 1002L));
    when(chatRepository.findById(5L)).thenReturn(Optional.of(chat));
  }

  private static Student student(String name, Long number) {
    Student student = ModelTestData.student(name);
    ReflectionTestUtils.setField(student, "number", number);
    return student;
  }

  @Test
  @DisplayName("A member gets the chat")
  void memberGetsChat() {
    // Act
    var result = chatService.getChatFor(5L, ana);

    // Assert
    assertSame(chat, result);
  }

  @Test
  @DisplayName("Someone who isn't in the chat gets NOT_FOUND")
  void nonMemberGetsNotFound() {
    // Arrange
    Student eva = student("Eva", 1003L);

    // Act
    StudyMatchException e = assertThrows(StudyMatchException.class, () -> chatService.getChatFor(5L, eva));

    // Assert
    assertEquals(ErrorCode.NOT_FOUND, e.getCode());
  }

  @Test
  @DisplayName("A chat that doesn't exist gives the same NOT_FOUND")
  void missingChatGetsNotFound() {
    // Arrange
    when(chatRepository.findById(9L)).thenReturn(Optional.empty());

    // Act
    StudyMatchException e = assertThrows(StudyMatchException.class, () -> chatService.getChatFor(9L, ana));

    // Assert
    assertEquals(ErrorCode.NOT_FOUND, e.getCode());
  }

  @Test
  @DisplayName("Both users find the same DM, the smaller number is always asked first")
  void directChatFromEitherSide() {
    // Arrange
    Student rui = (Student) chat.getSecondUser();
    when(chatRepository.findDirectChat(1001L, 1002L)).thenReturn(Optional.of(chat));

    // Act
    var fromAna = chatService.findDirectChat(ana, rui);
    var fromRui = chatService.findDirectChat(rui, ana);

    // Assert
    assertSame(chat, fromAna.orElseThrow());
    assertSame(chat, fromRui.orElseThrow());
  }

  @Test
  @DisplayName("No DM yet comes back empty")
  void noDirectChatYet() {
    // Arrange
    Student eva = student("Eva", 1003L);
    when(chatRepository.findDirectChat(1001L, 1003L)).thenReturn(Optional.empty());

    // Act
    var result = chatService.findDirectChat(eva, ana);

    // Assert
    assertTrue(result.isEmpty());
  }

  @Test
  @DisplayName("Sending to someone you already have a DM with uses that DM, nothing new is saved")
  void getOrCreateReusesDirectChat() {
    // Arrange
    Student rui = (Student) chat.getSecondUser();
    when(chatRepository.findDirectChat(1001L, 1002L)).thenReturn(Optional.of(chat));

    // Act
    DirectChat result = chatService.getOrCreateDirectChat(rui, ana);

    // Assert
    assertSame(chat, result);
    verify(chatRepository, never()).saveAndFlush(any());
  }

  @Test
  @DisplayName("Without a DM, the first message creates it (GR8)")
  void getOrCreateCreatesDirectChat() {
    // Arrange
    Student eva = student("Eva", 1003L);
    when(chatRepository.findDirectChat(1001L, 1003L)).thenReturn(Optional.empty());
    when(chatRepository.saveAndFlush(any(DirectChat.class))).thenAnswer(call -> call.getArgument(0));

    // Act
    DirectChat result = chatService.getOrCreateDirectChat(eva, ana);

    // Assert
    assertSame(ana, result.getFirstUser());
    assertSame(eva, result.getSecondUser());
  }

  @Test
  @DisplayName("Both sending the first message at once: the second gets DIRECT_CHAT_ALREADY_EXISTS")
  void getOrCreateRace() {
    // Arrange - the other side's DM was saved between the lookup and our insert
    Student eva = student("Eva", 1003L);
    when(chatRepository.findDirectChat(1001L, 1003L)).thenReturn(Optional.empty());
    when(chatRepository.saveAndFlush(any(DirectChat.class))).thenThrow(new DataIntegrityViolationException("pair"));

    // Act
    StudyMatchException e = assertThrows(StudyMatchException.class, () -> chatService.getOrCreateDirectChat(ana, eva));

    // Assert
    assertEquals(ErrorCode.DIRECT_CHAT_ALREADY_EXISTS, e.getCode());
  }

  @Test
  @DisplayName("The contacts are everyone the user shares a chat with, without the user")
  void contactsAreTheOtherMembers() {
    // Arrange
    Student eva = student("Eva", 1003L);
    when(chatRepository.findDirectChatsOf(1001L)).thenReturn(List.of(chat, new DirectChat(eva, ana)));

    // Act
    List<User> contacts = chatService.getContactsOf(ana);

    // Assert
    assertEquals(List.of(1002L, 1003L), contacts.stream().map(User::getNumber).toList());
  }

  @Test
  @DisplayName("Only the contacts with the app open are online")
  void onlineContactsOnly() {
    // Arrange
    Student eva = student("Eva", 1003L);
    when(chatRepository.findDirectChatsOf(1001L)).thenReturn(List.of(chat, new DirectChat(ana, eva)));
    presenceTracker.connect(1003L, "eva-tab");
    presenceTracker.connect(1004L, "stranger-tab");

    // Act
    List<Long> online = chatService.getOnlineContacts(ana);

    // Assert
    assertEquals(List.of(1003L), online);
  }

  @Test
  @DisplayName("The first tab tells each contact the user is online, the second tells nobody")
  void onlinePushedOnce() {
    // Arrange
    when(chatRepository.findDirectChatsOf(1001L)).thenReturn(List.of(chat));

    // Act
    chatService.userConnected(ana, "first-tab");
    chatService.userConnected(ana, "second-tab");

    // Assert
    verify(eventPublisher, times(1)).publishEvent(any(UserChatEvent.class));
    verify(eventPublisher).publishEvent(new UserChatEvent(chat.getOtherUser(ana), ChatEventType.USER_ONLINE, 1001L));
  }

  @Test
  @DisplayName("Only the last tab closing tells the contacts the user is offline")
  void offlinePushedOnLastTab() {
    // Arrange
    when(chatRepository.findDirectChatsOf(1001L)).thenReturn(List.of(chat));
    presenceTracker.connect(1001L, "first-tab");
    presenceTracker.connect(1001L, "second-tab");

    // Act
    chatService.userDisconnected(ana, "first-tab");
    chatService.userDisconnected(ana, "second-tab");

    // Assert
    verify(eventPublisher, times(1)).publishEvent(any(UserChatEvent.class));
    verify(eventPublisher).publishEvent(new UserChatEvent(chat.getOtherUser(ana), ChatEventType.USER_OFFLINE, 1001L));
  }

  @Test
  @DisplayName("Someone who blocked the user isn't among their online contacts (GR9)")
  void blockerHiddenFromTheBlocked() {
    // Arrange - rui (1002) blocked ana, eva (1003) didn't
    Student eva = student("Eva", 1003L);
    when(chatRepository.findDirectChatsOf(1001L)).thenReturn(List.of(chat, new DirectChat(ana, eva)));
    when(userBlockService.getBlockerNumbers(ana)).thenReturn(Set.of(1002L));
    presenceTracker.connect(1002L, "rui-tab");
    presenceTracker.connect(1003L, "eva-tab");

    // Act
    List<Long> online = chatService.getOnlineContacts(ana);

    // Assert
    assertEquals(List.of(1003L), online);
  }

  @Test
  @DisplayName("Someone the user blocked isn't among their online contacts (GR9)")
  void blockedHiddenFromTheBlocker() {
    // Arrange - ana blocked eva (1003), not rui (1002)
    Student eva = student("Eva", 1003L);
    when(chatRepository.findDirectChatsOf(1001L)).thenReturn(List.of(chat, new DirectChat(ana, eva)));
    when(userBlockService.getBlockedNumbers(ana)).thenReturn(Set.of(1003L));
    presenceTracker.connect(1002L, "rui-tab");
    presenceTracker.connect(1003L, "eva-tab");

    // Act
    List<Long> online = chatService.getOnlineContacts(ana);

    // Assert
    assertEquals(List.of(1002L), online);
  }

  @Test
  @DisplayName("Going online isn't pushed to someone who blocked the user (GR9)")
  void onlineNotPushedToTheBlocker() {
    // Arrange
    when(chatRepository.findDirectChatsOf(1001L)).thenReturn(List.of(chat));
    when(userBlockService.getBlockerNumbers(ana)).thenReturn(Set.of(1002L));

    // Act
    chatService.userConnected(ana, "first-tab");

    // Assert
    verify(eventPublisher, never()).publishEvent(any(UserChatEvent.class));
  }

  @Test
  @DisplayName("Typing in a DM whose other member blocked the user goes nowhere (GR9)")
  void blockedTypingIsDropped() {
    // Arrange
    when(userBlockService.hasBlocked(chat.getOtherUser(ana), ana)).thenReturn(true);

    // Act
    chatService.announceTyping(5L, ana);

    // Assert
    verify(eventPublisher, never()).publishEvent(any(ChatEvent.class));
  }

  @Test
  @DisplayName("Typing in a DM with nobody blocked reaches the chat")
  void typingIsPublished() {
    // Act
    chatService.announceTyping(5L, ana);

    // Assert
    verify(eventPublisher).publishEvent(any(ChatEvent.class));
  }

  @Test
  @DisplayName("A group chat is created with the creator as owner, and every member's list gets it")
  void groupChatCreated() {
    // Arrange
    Student rui = (Student) chat.getSecondUser();
    Student eva = student("Eva", 1003L);
    when(userService.getChatContacts(Set.of(1002L, 1003L), ana)).thenReturn(List.of(rui, eva));
    when(chatRepository.save(any(CustomChat.class))).thenAnswer(call -> call.getArgument(0));

    // Act
    CustomChat created = chatService.createGroupChat(ana, new CreateGroupChatRequestDTO("Study group", null,
        List.of(1002L, 1003L)));

    // Assert
    assertSame(ana, created.getOwner());
    assertEquals(List.of(ana, rui, eva), created.getMembers());
    verify(eventPublisher, times(3)).publishEvent(any(UserChatEvent.class));
  }

  @Test
  @DisplayName("Someone who blocked the creator can't be added, the error names them and nothing is saved (GR9)")
  void blockerCannotBeAdded() {
    // Arrange - eva (1003) blocked ana
    Student rui = (Student) chat.getSecondUser();
    Student eva = student("Eva", 1003L);
    when(userService.getChatContacts(Set.of(1002L, 1003L), ana)).thenReturn(List.of(rui, eva));
    when(userBlockService.getBlockerNumbers(ana)).thenReturn(Set.of(1003L));

    // Act
    StudyMatchException e = assertThrows(StudyMatchException.class, () -> chatService.createGroupChat(ana,
        new CreateGroupChatRequestDTO("Study group", null, List.of(1002L, 1003L))));

    // Assert
    assertEquals(ErrorCode.CANNOT_ADD_TO_GROUP_CHAT, e.getCode());
    assertEquals(List.of(1003L), e.getParams().get("numbers"));
    verify(chatRepository, never()).save(any());
    verify(eventPublisher, never()).publishEvent(any(UserChatEvent.class));
  }

  @Test
  @DisplayName("Someone the creator blocked can still be added, a block only hides DMs (GR9)")
  void blockedByCreatorCanBeAdded() {
    // Arrange - ana blocked eva (1003)
    Student rui = (Student) chat.getSecondUser();
    Student eva = student("Eva", 1003L);
    when(userService.getChatContacts(Set.of(1002L, 1003L), ana)).thenReturn(List.of(rui, eva));
    when(userBlockService.getBlockedNumbers(ana)).thenReturn(Set.of(1003L));
    when(chatRepository.save(any(CustomChat.class))).thenAnswer(call -> call.getArgument(0));

    // Act
    CustomChat created = chatService.createGroupChat(ana, new CreateGroupChatRequestDTO("Study group", null,
        List.of(1002L, 1003L)));

    // Assert
    assertTrue(created.hasMember(eva));
  }

  // ana, rui and eva, saved as chat 6
  private CustomChat groupChat(Student eva) {
    CustomChat group = new CustomChat("Study group", null, ana, List.of(chat.getSecondUser(), eva));
    when(chatRepository.findById(6L)).thenReturn(Optional.of(group));
    return group;
  }

  @Test
  @DisplayName("Typing in a group chat goes to each member, not to the topic a removed member's tab may still hear")
  void groupTypingGoesToEachMember() {
    // Arrange
    groupChat(student("Eva", 1003L));

    // Act
    chatService.announceTyping(6L, ana);

    // Assert
    verify(eventPublisher, times(3)).publishEvent(any(MemberChatEvent.class));
    verify(eventPublisher, never()).publishEvent(any(ChatEvent.class));
  }

  @Test
  @DisplayName("A DM's members can't be changed: CHAT_MEMBERS_FIXED")
  void directChatMembersFixed() {
    // Act
    StudyMatchException e = assertThrows(StudyMatchException.class,
        () -> chatService.addMembers(5L, ana, new AddMembersRequestDTO(List.of(1003L))));

    // Assert
    assertEquals(ErrorCode.CHAT_MEMBERS_FIXED, e.getCode());
  }

  @Test
  @DisplayName("The people added go in with the history already read, only they come back (GR8)")
  void membersAdded() {
    // Arrange
    Student eva = student("Eva", 1003L);
    Student zeca = student("Zeca", 1004L);
    CustomChat group = groupChat(eva);
    when(userService.getChatContacts(Set.of(1004L), ana)).thenReturn(List.of(zeca));

    // Act
    List<User> added = chatService.addMembers(6L, ana, new AddMembersRequestDTO(List.of(1004L)));

    // Assert
    assertEquals(List.of(zeca), added);
    assertTrue(group.hasMember(zeca));
    verify(chatSettingsService).startRead(group, List.of(zeca));
  }

  @Test
  @DisplayName("Someone new who blocked the user can't be added, and nobody is (GR9)")
  void blockerNotAdded() {
    // Arrange - zeca (1004) blocked ana, lia (1005) didn't
    Student eva = student("Eva", 1003L);
    Student zeca = student("Zeca", 1004L);
    Student lia = student("Lia", 1005L);
    CustomChat group = groupChat(eva);
    when(userService.getChatContacts(Set.of(1004L, 1005L), ana)).thenReturn(List.of(zeca, lia));
    when(userBlockService.getBlockerNumbers(ana)).thenReturn(Set.of(1004L));

    // Act
    StudyMatchException e = assertThrows(StudyMatchException.class,
        () -> chatService.addMembers(6L, ana, new AddMembersRequestDTO(List.of(1004L, 1005L))));

    // Assert
    assertEquals(ErrorCode.CANNOT_ADD_TO_GROUP_CHAT, e.getCode());
    assertEquals(3, group.getMembers().size());
    verify(chatSettingsService, never()).startRead(any(), any());
  }

  @Test
  @DisplayName("Picking someone already in who blocked the user is skipped, not refused")
  void currentMemberWhoBlockedIsSkipped() {
    // Arrange - eva (1003) is in and blocked ana
    Student eva = student("Eva", 1003L);
    Student zeca = student("Zeca", 1004L);
    groupChat(eva);
    when(userService.getChatContacts(Set.of(1004L), ana)).thenReturn(List.of(zeca));
    when(userBlockService.getBlockerNumbers(ana)).thenReturn(Set.of(1003L));

    // Act
    List<User> added = chatService.addMembers(6L, ana, new AddMembersRequestDTO(List.of(1003L, 1004L)));

    // Assert
    assertEquals(List.of(zeca), added);
  }

  @Test
  @DisplayName("The owner removes a member, who's out and gets CHAT_REMOVED (GR8)")
  void ownerRemovesMember() {
    // Arrange
    CustomChat group = groupChat(student("Eva", 1003L));
    User rui = chat.getSecondUser();

    // Act
    chatService.removeMember(6L, ana, 1002L);

    // Assert
    assertFalse(group.hasMember(rui));
    verify(chatSettingsService).deleteOf(group, rui);
    verify(eventPublisher).publishEvent(new UserChatEvent(rui, ChatEventType.CHAT_REMOVED, group.getId()));
  }

  @Test
  @DisplayName("Only the owner removes members: NOT_CHAT_OWNER")
  void onlyOwnerRemoves() {
    // Arrange
    CustomChat group = groupChat(student("Eva", 1003L));
    User rui = chat.getSecondUser();

    // Act
    StudyMatchException e = assertThrows(StudyMatchException.class, () -> chatService.removeMember(6L, rui, 1003L));

    // Assert
    assertEquals(ErrorCode.NOT_CHAT_OWNER, e.getCode());
    assertEquals(3, group.getMembers().size());
  }

  @Test
  @DisplayName("The owner can't remove themselves, they leave instead: CANNOT_REMOVE_YOURSELF")
  void ownerCannotRemoveThemselves() {
    // Arrange
    groupChat(student("Eva", 1003L));

    // Act
    StudyMatchException e = assertThrows(StudyMatchException.class, () -> chatService.removeMember(6L, ana, 1001L));

    // Assert
    assertEquals(ErrorCode.CANNOT_REMOVE_YOURSELF, e.getCode());
  }

  @Test
  @DisplayName("Removing someone who isn't in it changes nothing and tells nobody")
  void removingAStrangerTellsNobody() {
    // Arrange
    CustomChat group = groupChat(student("Eva", 1003L));

    // Act
    chatService.removeMember(6L, ana, 1004L);

    // Assert
    assertEquals(3, group.getMembers().size());
    verify(chatSettingsService, never()).deleteOf(any(), any());
    verify(eventPublisher, never()).publishEvent(any(UserChatEvent.class));
  }

  @Test
  @DisplayName("Nobody is removed from a DM: CHAT_MEMBERS_FIXED")
  void directChatRemoveFixed() {
    // Act
    StudyMatchException e = assertThrows(StudyMatchException.class, () -> chatService.removeMember(5L, ana, 1002L));

    // Assert
    assertEquals(ErrorCode.CHAT_MEMBERS_FIXED, e.getCode());
  }

  @Test
  @DisplayName("The owner leaving hands it to whoever's been there longest, their other tabs get CHAT_REMOVED")
  void ownerLeaves() {
    // Arrange
    CustomChat group = groupChat(student("Eva", 1003L));

    // Act
    chatService.leave(6L, ana);

    // Assert
    assertFalse(group.hasMember(ana));
    assertEquals(1002L, group.getOwner().getNumber());
    verify(chatSettingsService).deleteOf(group, ana);
    verify(eventPublisher).publishEvent(new UserChatEvent(ana, ChatEventType.CHAT_REMOVED, group.getId()));
  }

  @Test
  @DisplayName("Nobody leaves a DM: CHAT_MEMBERS_FIXED")
  void directChatLeaveFixed() {
    // Act
    StudyMatchException e = assertThrows(StudyMatchException.class, () -> chatService.leave(5L, ana));

    // Assert
    assertEquals(ErrorCode.CHAT_MEMBERS_FIXED, e.getCode());
  }

  @Test
  @DisplayName("Any member renames a group chat, not only the owner, and every member's list gets CHAT_EDITED")
  void memberRenames() {
    // Arrange
    Student eva = student("Eva", 1003L);
    CustomChat group = groupChat(eva);
    User rui = chat.getSecondUser();

    // Act
    chatService.rename(6L, rui, new RenameGroupChatRequestDTO("Exam prep"));

    // Assert
    assertEquals("Exam prep", group.getName());
    ChatEditedDTO edited = new ChatEditedDTO(group.getId(), "Exam prep", false);
    for (User member : List.of(ana, rui, eva)) {
      verify(eventPublisher).publishEvent(new UserChatEvent(member, ChatEventType.CHAT_EDITED, edited));
    }
  }

  @Test
  @DisplayName("Editing locks the chat first, saving writes the owner back too")
  void editingLocks() {
    // Arrange
    groupChat(student("Eva", 1003L));

    // Act
    chatService.rename(6L, ana, new RenameGroupChatRequestDTO("Exam prep"));

    // Assert
    verify(chatRepository).findLockedCustomChat(6L);
  }

  @Test
  @DisplayName("A DM has no name to change: NOT_FOUND")
  void directChatRenameNotFound() {
    // Act
    StudyMatchException e = assertThrows(StudyMatchException.class,
        () -> chatService.rename(5L, ana, new RenameGroupChatRequestDTO("Exam prep")));

    // Assert
    assertEquals(ErrorCode.NOT_FOUND, e.getCode());
  }

  @Test
  @DisplayName("The owner changes the picture and every member's list gets CHAT_EDITED with it")
  void ownerChangesPicture() {
    // Arrange
    CustomChat group = groupChat(student("Eva", 1003L));

    // Act
    chatService.changePicture(6L, ana, new GroupChatPictureRequestDTO(PICTURE));

    // Assert
    assertEquals(PICTURE, group.getPicture());
    verify(eventPublisher).publishEvent(new UserChatEvent(ana, ChatEventType.CHAT_EDITED,
        new ChatEditedDTO(group.getId(), "Study group", true)));
  }

  @Test
  @DisplayName("The owner sending no picture takes it off")
  void ownerRemovesPicture() {
    // Arrange
    CustomChat group = groupChat(student("Eva", 1003L));
    group.setPicture(PICTURE);

    // Act
    chatService.changePicture(6L, ana, new GroupChatPictureRequestDTO(null));

    // Assert
    assertNull(group.getPicture());
    verify(eventPublisher).publishEvent(new UserChatEvent(ana, ChatEventType.CHAT_EDITED,
        new ChatEditedDTO(group.getId(), "Study group", false)));
  }

  @Test
  @DisplayName("Only the owner changes the picture: NOT_CHAT_OWNER, and nobody's told")
  void onlyOwnerChangesPicture() {
    // Arrange
    CustomChat group = groupChat(student("Eva", 1003L));
    User rui = chat.getSecondUser();

    // Act
    StudyMatchException e = assertThrows(StudyMatchException.class,
        () -> chatService.changePicture(6L, rui, new GroupChatPictureRequestDTO(PICTURE)));

    // Assert
    assertEquals(ErrorCode.NOT_CHAT_OWNER, e.getCode());
    assertNull(group.getPicture());
    verify(eventPublisher, never()).publishEvent(any(UserChatEvent.class));
  }

  @Test
  @DisplayName("A picture that isn't a PNG or JPEG is refused like creating: INVALID_GROUP_CHAT_PICTURE")
  void invalidPictureRefused() {
    // Arrange
    groupChat(student("Eva", 1003L));

    // Act
    StudyMatchException e = assertThrows(StudyMatchException.class,
        () -> chatService.changePicture(6L, ana, new GroupChatPictureRequestDTO("data:image/png;base64,aGVsbG8=")));

    // Assert
    assertEquals(ErrorCode.INVALID_GROUP_CHAT_PICTURE, e.getCode());
    verify(eventPublisher, never()).publishEvent(any(UserChatEvent.class));
  }

  @Test
  @DisplayName("A DM has no picture of its own to change: NOT_FOUND")
  void directChatPictureNotFound() {
    // Act
    StudyMatchException e = assertThrows(StudyMatchException.class,
        () -> chatService.changePicture(5L, ana, new GroupChatPictureRequestDTO(PICTURE)));

    // Assert
    assertEquals(ErrorCode.NOT_FOUND, e.getCode());
  }

  @Test
  @DisplayName("Deleting a chat takes everyone's settings with it, then the chat")
  void deleteTakesSettings() {
    // Arrange
    CustomChat group = groupChat(student("Eva", 1003L));
    ReflectionTestUtils.setField(group, "id", 6L);

    // Act
    chatService.deleteWithSettings(group);

    // Assert
    verify(chatSettingsService).deleteAllOf(group);
    verify(chatRepository).deleteById(6L);
  }

  @Test
  @DisplayName("Too many numbers are refused before anyone is looked up, so a huge list never reaches the query")
  void tooManyRefusedBeforeLookup() {
    // Arrange - 20 others makes 21 with the creator
    List<Long> numbers = LongStream.range(2001, 2021).boxed().toList();

    // Act
    StudyMatchException e = assertThrows(StudyMatchException.class,
        () -> chatService.createGroupChat(ana, new CreateGroupChatRequestDTO("Study group", null, numbers)));

    // Assert
    assertEquals(ErrorCode.TOO_MANY_GROUP_CHAT_MEMBERS, e.getCode());
    verify(userService, never()).getChatContacts(any(), any());
  }

  @Test
  @DisplayName("Adding too many is refused before anyone is looked up, people already in don't count")
  void addingTooManyRefusedBeforeLookup() {
    // Arrange - 3 in, the 3 picked again plus 18 new makes 21
    groupChat(student("Eva", 1003L));
    List<Long> numbers = new ArrayList<>(List.of(1001L, 1002L, 1003L));
    numbers.addAll(LongStream.range(2001, 2019).boxed().toList());

    // Act
    StudyMatchException e = assertThrows(StudyMatchException.class,
        () -> chatService.addMembers(6L, ana, new AddMembersRequestDTO(numbers)));

    // Assert
    assertEquals(ErrorCode.TOO_MANY_GROUP_CHAT_MEMBERS, e.getCode());
    verify(userService, never()).getChatContacts(any(), any());
  }

  @Test
  @DisplayName("Picking the same person twice adds them once")
  void duplicatesCollapse() {
    // Arrange
    Student rui = (Student) chat.getSecondUser();
    Student eva = student("Eva", 1003L);
    when(userService.getChatContacts(Set.of(1002L, 1003L), ana)).thenReturn(List.of(rui, eva));
    when(chatRepository.save(any(CustomChat.class))).thenAnswer(call -> call.getArgument(0));

    // Act
    CustomChat created = chatService.createGroupChat(ana, new CreateGroupChatRequestDTO("Study group", null,
        List.of(1002L, 1003L, 1002L)));

    // Assert
    assertEquals(3, created.getMembers().size());
  }

}
