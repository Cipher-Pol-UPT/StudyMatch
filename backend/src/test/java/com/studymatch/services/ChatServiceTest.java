package com.studymatch.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import com.studymatch.exceptions.ErrorCode;
import com.studymatch.exceptions.StudyMatchException;
import com.studymatch.models.DirectChat;
import com.studymatch.models.ModelTestData;
import com.studymatch.models.Student;
import com.studymatch.repositories.ChatRepository;

class ChatServiceTest {

  private ChatRepository chatRepository;
  private ChatService chatService;

  private Student ana;
  private DirectChat chat;

  @BeforeEach
  void setUp() {
    chatRepository = mock(ChatRepository.class);
    chatService = new ChatService(chatRepository);

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

}
