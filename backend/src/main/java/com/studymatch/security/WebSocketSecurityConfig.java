package com.studymatch.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.annotation.web.socket.EnableWebSocketSecurity;
import org.springframework.security.messaging.access.intercept.MessageMatcherDelegatingAuthorizationManager;
import org.springframework.security.messaging.web.csrf.CsrfChannelInterceptor;

// spring security on every STOMP frame, not just the handshake
@Configuration
@EnableWebSocketSecurity
public class WebSocketSecurityConfig {

  // CONNECT carries the XSRF-TOKEN cookie as it is, like csrf.spa() takes on REST, not the masked version
  @Bean
  public ChannelInterceptor csrfChannelInterceptor() {
    return new CsrfChannelInterceptor();
  }

  // the browser only listens, pushes come from the server, so sending to /topic or /queue is refused.
  // a chat topic needs a numeric id, so a pattern like /topic/chats/* can't listen to every chat
  @Bean
  public AuthorizationManager<Message<?>> messageAuthorizationManager(
      MessageMatcherDelegatingAuthorizationManager.Builder messages) {
    return messages
        .nullDestMatcher().authenticated()
        .simpSubscribeDestMatchers("/topic/chats/{chatId:\\d+}", "/user/queue/chats",
            "/user/queue/chats/{chatId:\\d+}").authenticated()
        .simpDestMatchers("/app/**").authenticated()
        .anyMessage().denyAll()
        .build();
  }

}
