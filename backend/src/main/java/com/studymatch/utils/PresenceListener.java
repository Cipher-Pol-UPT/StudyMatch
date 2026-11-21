package com.studymatch.utils;

import java.security.Principal;
import java.util.Optional;

import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import com.studymatch.models.User;
import com.studymatch.services.ChatService;
import com.studymatch.services.UserService;

// a tab opening or closing its websocket, that's what online means
@Component
public class PresenceListener {

  private final ChatService chatService;
  private final UserService userService;

  public PresenceListener(ChatService chatService, UserService userService) {
    this.chatService = chatService;
    this.userService = userService;
  }

  @EventListener
  public void onConnected(SessionConnectedEvent event) {
    String sessionId = StompHeaderAccessor.wrap(event.getMessage()).getSessionId();
    userOf(event.getUser()).ifPresent(user -> chatService.userConnected(user, sessionId));
  }

  @EventListener
  public void onDisconnected(SessionDisconnectEvent event) {
    userOf(event.getUser()).ifPresent(user -> chatService.userDisconnected(user, event.getSessionId()));
  }

  // the login is their institutional email, same as the relay uses
  private Optional<User> userOf(Principal principal) {
    return principal == null ? Optional.empty() : userService.findByInstitutionalEmail(principal.getName());
  }

}
