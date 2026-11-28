package com.studymatch.services;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.studymatch.enums.ChatEventType;
import com.studymatch.enums.NotificationLevel;
import com.studymatch.events.UserChatEvent;
import com.studymatch.models.Chat;
import com.studymatch.models.ChatSettings;
import com.studymatch.models.User;
import com.studymatch.repositories.ChatSettingsRepository;
import com.studymatch.repositories.ChatSettingsRepository.MemberLevel;

@Service
public class ChatSettingsService {

  private final ChatSettingsRepository chatSettingsRepository;
  private final NotificationService notificationService;
  private final ApplicationEventPublisher eventPublisher;

  public ChatSettingsService(ChatSettingsRepository chatSettingsRepository, NotificationService notificationService,
      ApplicationEventPublisher eventPublisher) {
    this.chatSettingsRepository = chatSettingsRepository;
    this.notificationService = notificationService;
    this.eventPublisher = eventPublisher;
  }

  // everything sent up to now stops counting as unread, its notification too (GR11), and the user's other tabs
  // clear the badge
  @Transactional
  public void markRead(Chat chat, User user) {
    ChatSettings settings = new ChatSettings(chat, user);
    settings.markRead();
    chatSettingsRepository.saveLastReadAt(settings);
    notificationService.markChatRead(user, chat.getId());
    eventPublisher.publishEvent(new UserChatEvent(user, ChatEventType.CHAT_READ, chat.getId()));
  }

  // ALL until they pick another (GR11)
  @Transactional(readOnly = true)
  public NotificationLevel getLevel(Chat chat, User user) {
    return chatSettingsRepository.findByChatAndUser(chat, user)
        .map(ChatSettings::getNotificationLevel)
        .orElse(NotificationLevel.ALL);
  }

  @Transactional
  public void setLevel(Chat chat, User user, NotificationLevel level) {
    chatSettingsRepository.upsertNotificationLevel(chat.getId(), user.getNumber(), level.name());
  }

  // every member's level by number, the ones without a row aren't in it and are on ALL
  @Transactional(readOnly = true)
  public Map<Long, NotificationLevel> getLevels(Chat chat) {
    return chatSettingsRepository.findLevels(chat).stream()
        .collect(Collectors.toMap(MemberLevel::getNumber, MemberLevel::getLevel));
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
