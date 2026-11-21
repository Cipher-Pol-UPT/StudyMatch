package com.studymatch.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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

}
