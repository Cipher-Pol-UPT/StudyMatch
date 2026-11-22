package com.studymatch.services;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.studymatch.dtos.ChatContactDTO;
import com.studymatch.dtos.TypingDTO;
import com.studymatch.enums.ChatEventType;
import com.studymatch.events.ChatEvent;
import com.studymatch.events.UserChatEvent;
import com.studymatch.exceptions.ErrorCode;
import com.studymatch.exceptions.StudyMatchException;
import com.studymatch.models.Chat;
import com.studymatch.models.DirectChat;
import com.studymatch.models.User;
import com.studymatch.repositories.ChatRepository;
import com.studymatch.utils.PresenceTracker;

@Service
public class ChatService {

  private final ChatRepository chatRepository;
  private final UserBlockService userBlockService;
  private final PresenceTracker presenceTracker;
  private final ApplicationEventPublisher eventPublisher;

  public ChatService(ChatRepository chatRepository, UserBlockService userBlockService,
      PresenceTracker presenceTracker, ApplicationEventPublisher eventPublisher) {
    this.chatRepository = chatRepository;
    this.userBlockService = userBlockService;
    this.presenceTracker = presenceTracker;
    this.eventPublisher = eventPublisher;
  }

  // not a member looks the same as no chat, so nobody finds out which chats exist (GR8).
  // a DM with someone you blocked is gone too while the block lasts (GR9)
  @Transactional(readOnly = true)
  public Chat getChatFor(Long chatId, User user) {
    return chatRepository.findById(chatId)
        .filter(chat -> chat.hasMember(user))
        .filter(chat -> !blockedDirectChat(chat, user))
        .orElseThrow(() -> new StudyMatchException(ErrorCode.NOT_FOUND));
  }

  private boolean blockedDirectChat(Chat chat, User user) {
    return chat instanceof DirectChat direct && userBlockService.hasBlocked(user, direct.getOtherUser(user));
  }

  // every chat the user is in, without DMs with people they blocked. a block doesn't hide group chats (GR9).
  // assignment group chats join this list when they exist (#236)
  @Transactional(readOnly = true)
  public List<Chat> getChatsOf(User user) {
    Set<Long> blocked = userBlockService.getBlockedNumbers(user);
    List<Chat> chats = new ArrayList<>(chatRepository.findDirectChatsOf(user.getNumber()).stream()
        .filter(direct -> !blocked.contains(direct.getOtherUser(user).getNumber()))
        .toList());
    chats.addAll(chatRepository.findCustomChatsOf(user.getNumber()));
    return chats;
  }

  // everyone the user shares a chat with, once each, they're who sees them online.
  // a block either way hides it both ways, a dot that stops changing would give the block away (GR9)
  @Transactional(readOnly = true)
  public List<User> getContactsOf(User user) {
    Set<Long> hidden = new HashSet<>(userBlockService.getBlockedNumbers(user));
    hidden.addAll(userBlockService.getBlockerNumbers(user));
    Map<Long, User> contacts = new LinkedHashMap<>();
    for (Chat chat : getChatsOf(user)) {
      for (User member : chat.getMembers()) {
        if (!member.getNumber().equals(user.getNumber()) && !hidden.contains(member.getNumber())) {
          contacts.putIfAbsent(member.getNumber(), member);
        }
      }
    }
    return new ArrayList<>(contacts.values());
  }

  // tells whoever has the chat open, members only (GR8). the frontend drops it after 5 seconds without another
  @Transactional(readOnly = true)
  public void announceTyping(Long chatId, User user) {
    Chat chat = getChatFor(chatId, user);
    // the blocker's tab from before the block still listens to the topic, so the blocked user's typing goes nowhere
    if (chat instanceof DirectChat direct && userBlockService.hasBlocked(direct.getOtherUser(user), user)) {
      return;
    }
    eventPublisher.publishEvent(new ChatEvent(chat.getId(), ChatEventType.TYPING,
        new TypingDTO(chat.getId(), ChatContactDTO.from(user))));
  }

  // their first tab puts them online for everyone they share a chat with. read only, but the pushes need a commit
  @Transactional(readOnly = true)
  public void userConnected(User user, String sessionId) {
    if (presenceTracker.connect(user.getNumber(), sessionId)) {
      tellContacts(user, ChatEventType.USER_ONLINE);
    }
  }

  // and their last one closing puts them offline
  @Transactional(readOnly = true)
  public void userDisconnected(User user, String sessionId) {
    if (presenceTracker.disconnect(user.getNumber(), sessionId)) {
      tellContacts(user, ChatEventType.USER_OFFLINE);
    }
  }

  // the dots for the chat list's first load, the pushes keep it up to date after that
  @Transactional(readOnly = true)
  public List<Long> getOnlineContacts(User user) {
    return getContactsOf(user).stream()
        .map(User::getNumber)
        .filter(presenceTracker::isOnline)
        .sorted()
        .toList();
  }

  private void tellContacts(User user, ChatEventType type) {
    for (User contact : getContactsOf(user)) {
      eventPublisher.publishEvent(new UserChatEvent(contact, type, user.getNumber()));
    }
  }

  // empty until the first message creates it (GR8)
  @Transactional(readOnly = true)
  public Optional<DirectChat> findDirectChat(User user, User other) {
    long first = Math.min(user.getNumber(), other.getNumber());
    long second = Math.max(user.getNumber(), other.getNumber());
    return chatRepository.findDirectChat(first, second);
  }

  // the first message creates the DM (GR8), both sending the first one at once trips the unique pair
  @Transactional
  public DirectChat getOrCreateDirectChat(User user, User other) {
    return findDirectChat(user, other).orElseGet(() -> {
      try {
        return chatRepository.saveAndFlush(new DirectChat(user, other));
      } catch (DataIntegrityViolationException e) {
        throw new StudyMatchException(ErrorCode.DIRECT_CHAT_ALREADY_EXISTS);
      }
    });
  }

}
