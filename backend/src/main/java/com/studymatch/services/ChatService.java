package com.studymatch.services;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.studymatch.exceptions.ErrorCode;
import com.studymatch.exceptions.StudyMatchException;
import com.studymatch.models.Chat;
import com.studymatch.models.DirectChat;
import com.studymatch.models.User;
import com.studymatch.repositories.ChatRepository;

@Service
public class ChatService {

  private final ChatRepository chatRepository;
  private final UserBlockService userBlockService;

  public ChatService(ChatRepository chatRepository, UserBlockService userBlockService) {
    this.chatRepository = chatRepository;
    this.userBlockService = userBlockService;
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

  // every chat the user is in, without DMs with people they blocked (GR9).
  // group chats join this list when they exist (#226, #236)
  @Transactional(readOnly = true)
  public List<Chat> getChatsOf(User user) {
    Set<Long> blocked = userBlockService.getBlockedNumbers(user);
    return new ArrayList<>(chatRepository.findDirectChatsOf(user.getNumber()).stream()
        .filter(direct -> !blocked.contains(direct.getOtherUser(user).getNumber()))
        .toList());
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
