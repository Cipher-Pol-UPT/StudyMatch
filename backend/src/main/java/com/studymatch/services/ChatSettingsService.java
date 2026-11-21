package com.studymatch.services;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.studymatch.enums.ChatEventType;
import com.studymatch.events.UserChatEvent;
import com.studymatch.models.Chat;
import com.studymatch.models.ChatSettings;
import com.studymatch.models.User;
import com.studymatch.repositories.ChatSettingsRepository;

@Service
public class ChatSettingsService {

  private final ChatSettingsRepository chatSettingsRepository;
  private final ApplicationEventPublisher eventPublisher;

  public ChatSettingsService(ChatSettingsRepository chatSettingsRepository, ApplicationEventPublisher eventPublisher) {
    this.chatSettingsRepository = chatSettingsRepository;
    this.eventPublisher = eventPublisher;
  }

  // everything sent up to now stops counting as unread (GR11), and the user's other tabs clear the badge
  @Transactional
  public void markRead(Chat chat, User user) {
    ChatSettings settings = new ChatSettings(chat, user);
    settings.markRead();
    chatSettingsRepository.saveLastReadAt(settings);
    eventPublisher.publishEvent(new UserChatEvent(user, ChatEventType.CHAT_READ, chat.getId()));
  }

}
