package com.studymatch.models;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class MessageTest {

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
  @DisplayName("A new message has its chat, author, text and send time, and isn't edited")
  void newMessage() {
    // Act
    Message message = new Message(chat, ana, "hello");

    // Assert
    assertSame(chat, message.getChat());
    assertSame(ana, message.getAuthor());
    assertEquals("hello", message.getText());
    assertNotNull(message.getSentAt());
    assertNull(message.getEditedAt());
    assertFalse(message.isEdited());
  }

  @Test
  @DisplayName("Editing changes the text and marks it as edited")
  void editMarksEdited() {
    // Arrange
    Message message = new Message(chat, ana, "helo");

    // Act
    message.edit("hello");

    // Assert
    assertEquals("hello", message.getText());
    assertTrue(message.isEdited());
    assertNotNull(message.getEditedAt());
  }

  @Test
  @DisplayName("A message isn't a reply unless it answers another")
  void notAReply() {
    // Act
    Message message = new Message(chat, ana, "hello", null);

    // Assert
    assertFalse(message.isReply());
    assertNull(message.getReplyTo());
    assertFalse(message.quotesHidden());
  }

  @Test
  @DisplayName("A reply keeps its original, and knows when that one is hidden from someone (GR9)")
  void replyKeepsItsOriginal() {
    // Arrange
    Message original = new Message(chat, ana, "hello");

    // Act
    Message reply = new Message(chat, ana, "anyone?", original);

    // Assert
    assertTrue(reply.isReply());
    assertSame(original, reply.getReplyTo());
    assertFalse(reply.quotesHidden());
    original.hideFrom(chat.getSecondUser());
    assertTrue(reply.quotesHidden());
  }

  @Test
  @DisplayName("A message mentions whoever has a mentioned number, even loaded as another object, and no one else")
  void mentions() {
    // Arrange
    Student eva = ModelTestData.student("Eva");
    ReflectionTestUtils.setField(eva, "number", 1003L);
    Student evaAgain = ModelTestData.student("Eva");
    ReflectionTestUtils.setField(evaAgain, "number", 1003L);
    Message message = new Message(chat, ana, "@Eva hi");

    // Act
    message.mention(List.of(eva));

    // Assert
    assertTrue(message.mentions(evaAgain));
    assertFalse(message.mentions(ana));
  }

  @Test
  @DisplayName("A message mentions no one until someone is picked")
  void noMentions() {
    // Act
    Message message = new Message(chat, ana, "hi");

    // Assert
    assertTrue(message.getMentions().isEmpty());
    assertFalse(message.mentions(ana));
  }

}
