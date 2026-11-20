package com.studymatch.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import com.studymatch.exceptions.ErrorCode;
import com.studymatch.exceptions.StudyMatchException;
import com.studymatch.models.Teacher;
import com.studymatch.services.ChatService;
import com.studymatch.services.UserService;

class ChatSubscriptionInterceptorTest {

  private static final String EMAIL = "user@test.com";

  private ChatService chatService;
  private UserService userService;
  private ChatSubscriptionInterceptor interceptor;

  @BeforeEach
  void setUp() {
    chatService = mock(ChatService.class);
    userService = mock(UserService.class);
    interceptor = new ChatSubscriptionInterceptor(chatService, userService);
  }

  private static Message<byte[]> frame(StompCommand command, String destination) {
    StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
    accessor.setDestination(destination);
    accessor.setUser(UsernamePasswordAuthenticationToken.authenticated(EMAIL, null, null));
    accessor.setLeaveMutable(true);
    return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
  }

  @Test
  @DisplayName("A member subscribing to the chat's exact id gets through")
  void memberSubscribes() {
    // Arrange
    Teacher teacher = new Teacher("t@example.com", EMAIL, "Test Teacher", "hash");
    when(userService.findByInstitutionalEmail(EMAIL)).thenReturn(Optional.of(teacher));
    Message<byte[]> message = frame(StompCommand.SUBSCRIBE, "/topic/chats/5");

    // Act
    Message<?> result = interceptor.preSend(message, null);

    // Assert
    assertSame(message, result);
    verify(chatService).getChatFor(5L, teacher);
  }

  @Test
  @DisplayName("Subscribing to a chat you're not in is refused with NOT_FOUND")
  void nonMemberIsRefused() {
    // Arrange
    Teacher teacher = new Teacher("t@example.com", EMAIL, "Test Teacher", "hash");
    when(userService.findByInstitutionalEmail(EMAIL)).thenReturn(Optional.of(teacher));
    when(chatService.getChatFor(5L, teacher)).thenThrow(new StudyMatchException(ErrorCode.NOT_FOUND));
    Message<byte[]> message = frame(StompCommand.SUBSCRIBE, "/topic/chats/5");

    // Act
    StudyMatchException exception = assertThrows(StudyMatchException.class, () -> interceptor.preSend(message, null));

    // Assert
    assertEquals(ErrorCode.NOT_FOUND, exception.getCode());
  }

  @ParameterizedTest
  @ValueSource(strings = { "/topic/chats/*", "/topic/chats/**", "/topic/chats/{id}", "/topic/chats/?", "/topic/chats/5*",
      "/topic/chats/", "/topic/**", "/topic/*/5" })
  @DisplayName("A topic that isn't an exact chat id is refused before looking anything up (GR8)")
  void patternIsRefused(String destination) {
    // Arrange
    Message<byte[]> message = frame(StompCommand.SUBSCRIBE, destination);

    // Act
    StudyMatchException exception = assertThrows(StudyMatchException.class, () -> interceptor.preSend(message, null));

    // Assert
    assertEquals(ErrorCode.NOT_FOUND, exception.getCode());
    verifyNoInteractions(userService, chatService);
  }

  @Test
  @DisplayName("A user who no longer exists can't subscribe to a chat")
  void unknownUserIsRefused() {
    // Arrange
    when(userService.findByInstitutionalEmail(EMAIL)).thenReturn(Optional.empty());
    Message<byte[]> message = frame(StompCommand.SUBSCRIBE, "/topic/chats/5");

    // Act
    StudyMatchException exception = assertThrows(StudyMatchException.class, () -> interceptor.preSend(message, null));

    // Assert
    assertEquals(ErrorCode.UNAUTHENTICATED, exception.getCode());
    verifyNoInteractions(chatService);
  }

  @Test
  @DisplayName("A SUBSCRIBE without a destination is left to spring")
  void noDestinationGoesThrough() {
    // Arrange
    Message<byte[]> message = frame(StompCommand.SUBSCRIBE, null);

    // Act
    Message<?> result = interceptor.preSend(message, null);

    // Assert
    assertSame(message, result);
    verifyNoInteractions(userService, chatService);
  }

  @Test
  @DisplayName("The user's own queue isn't checked here")
  void userQueueGoesThrough() {
    // Arrange
    Message<byte[]> message = frame(StompCommand.SUBSCRIBE, "/user/queue/chats");

    // Act
    Message<?> result = interceptor.preSend(message, null);

    // Assert
    assertSame(message, result);
    verifyNoInteractions(userService, chatService);
  }

  @Test
  @DisplayName("Frames that aren't SUBSCRIBE go through untouched")
  void otherCommandsGoThrough() {
    // Arrange
    Message<byte[]> message = frame(StompCommand.SEND, "/topic/chats/*");

    // Act
    Message<?> result = interceptor.preSend(message, null);

    // Assert
    assertSame(message, result);
    verifyNoInteractions(userService, chatService);
  }

}
