package com.studymatch.models;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.studymatch.enums.NotificationLevel;

class ChatSettingsTest {

  private Student ana;
  private DirectChat chat;

  @BeforeEach
  void setUp() {
    ana = ModelTestData.student("Ana");
    Student rui = ModelTestData.student("Rui");
    ReflectionTestUtils.setField(ana, "number", 1001L);
    ReflectionTestUtils.setField(rui, "number", 1002L);
    chat = new DirectChat(ana, rui);
  }

  @Test
  @DisplayName("New settings notify about everything and nothing is read yet")
  void defaults() {
    // Act
    ChatSettings settings = new ChatSettings(chat, ana);

    // Assert
    assertSame(chat, settings.getChat());
    assertSame(ana, settings.getUser());
    assertEquals(NotificationLevel.ALL, settings.getNotificationLevel());
    assertNull(settings.getLastReadAt());
  }

  @Test
  @DisplayName("Opening the chat sets when it was last read")
  void markRead() {
    // Arrange
    ChatSettings settings = new ChatSettings(chat, ana);

    // Act
    settings.markRead();

    // Assert
    assertNotNull(settings.getLastReadAt());
  }

  @Test
  @DisplayName("The notification level can be changed")
  void changeLevel() {
    // Arrange
    ChatSettings settings = new ChatSettings(chat, ana);

    // Act
    settings.setNotificationLevel(NotificationLevel.MENTIONS);

    // Assert
    assertEquals(NotificationLevel.MENTIONS, settings.getNotificationLevel());
  }

}
