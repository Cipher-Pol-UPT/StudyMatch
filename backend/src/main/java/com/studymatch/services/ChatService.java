package com.studymatch.services;

import java.util.Optional;

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

  public ChatService(ChatRepository chatRepository) {
    this.chatRepository = chatRepository;
  }

  // not a member looks the same as no chat, so nobody finds out which chats exist (GR8)
  @Transactional(readOnly = true)
  public Chat getChatFor(Long chatId, User user) {
    return chatRepository.findById(chatId)
        .filter(chat -> chat.hasMember(user))
        .orElseThrow(() -> new StudyMatchException(ErrorCode.NOT_FOUND));
  }

  // empty until the first message creates it (GR8)
  @Transactional(readOnly = true)
  public Optional<DirectChat> findDirectChat(User user, User other) {
    long first = Math.min(user.getNumber(), other.getNumber());
    long second = Math.max(user.getNumber(), other.getNumber());
    return chatRepository.findDirectChat(first, second);
  }

}
