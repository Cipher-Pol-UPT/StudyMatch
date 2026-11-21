package com.studymatch.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import com.studymatch.security.ChatSubscriptionInterceptor;

// the websocket only pushes events, sending and editing stay REST
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

  private final String frontendUrl;
  private final ChatSubscriptionInterceptor chatSubscriptionInterceptor;

  public WebSocketConfig(@Value("${studymatch.frontend-url}") String frontendUrl,
      ChatSubscriptionInterceptor chatSubscriptionInterceptor) {
    this.frontendUrl = frontendUrl;
    this.chatSubscriptionInterceptor = chatSubscriptionInterceptor;
  }

  @Override
  public void registerStompEndpoints(StompEndpointRegistry registry) {
    registry.addEndpoint("/ws").setAllowedOrigins(frontendUrl);
  }

  // /topic/chats/{id} for a chat's events, /user/queue/chats for a user's chat list, /app for typing (#222)
  @Override
  public void configureMessageBroker(MessageBrokerRegistry registry) {
    registry.enableSimpleBroker("/topic", "/queue");
    registry.setApplicationDestinationPrefixes("/app");
  }

  @Override
  public void configureClientInboundChannel(ChannelRegistration registration) {
    registration.interceptors(chatSubscriptionInterceptor);
  }

}
