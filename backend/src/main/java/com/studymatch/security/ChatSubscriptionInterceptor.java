package com.studymatch.security;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.stereotype.Component;

import com.studymatch.exceptions.ErrorCode;
import com.studymatch.exceptions.StudyMatchException;
import com.studymatch.models.User;
import com.studymatch.services.ChatService;
import com.studymatch.services.UserService;

// only a chat's members can listen to it (GR8)
@Component
public class ChatSubscriptionInterceptor implements ChannelInterceptor {

  private static final Pattern CHAT_TOPIC = Pattern.compile("^/topic/chats/(\\d++)$");

  private final ChatService chatService;
  private final UserService userService;

  public ChatSubscriptionInterceptor(ChatService chatService, UserService userService) {
    this.chatService = chatService;
    this.userService = userService;
  }

  @Override
  public Message<?> preSend(Message<?> message, MessageChannel channel) {
    StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
    if (!StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
      return message;
    }

    Matcher chatTopic = CHAT_TOPIC.matcher(accessor.getDestination());
    if (chatTopic.matches()) {
      User user = userService.findByInstitutionalEmail(accessor.getUser().getName())
          .orElseThrow(() -> new StudyMatchException(ErrorCode.UNAUTHENTICATED));
      chatService.getChatFor(Long.valueOf(chatTopic.group(1)), user);
    }
    return message;
  }

}
