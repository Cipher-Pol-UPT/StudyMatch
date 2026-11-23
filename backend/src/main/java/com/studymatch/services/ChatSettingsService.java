package com.studymatch.services;

import java.util.List;

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

  // the chat is being deleted, everyone's settings go with it
  @Transactional
  public void deleteAllOf(Chat chat) {
    chatSettingsRepository.deleteByChat(chat);
  }

  // they left or were removed, if they're added back they start fresh, level included (GR11)
  @Transactional
  public void deleteOf(Chat chat, User user) {
    chatSettingsRepository.deleteByChatAndUser(chat, user);
  }

  // people added to a group chat start with the history read, it's there to scroll but doesn't count as unread.
  // no CHAT_READ, they don't have the chat yet
  @Transactional
  public void startRead(Chat chat, List<User> users) {
    for (User user : users) {
      ChatSettings settings = new ChatSettings(chat, user);
      settings.markRead();
      chatSettingsRepository.saveLastReadAt(settings);
    }
  }

}
