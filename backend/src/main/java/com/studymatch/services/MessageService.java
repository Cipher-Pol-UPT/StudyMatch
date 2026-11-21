package com.studymatch.services;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.studymatch.dtos.MessageDTO;
import com.studymatch.enums.ChatEventType;
import com.studymatch.events.ChatEvent;
import com.studymatch.events.UserChatEvent;
import com.studymatch.models.Chat;
import com.studymatch.models.Message;
import com.studymatch.models.User;
import com.studymatch.repositories.MessageRepository;

@Service
public class MessageService {

  private final MessageRepository messageRepository;
  private final ChatService chatService;
  private final UserService userService;
  private final ApplicationEventPublisher eventPublisher;

  public MessageService(MessageRepository messageRepository, ChatService chatService, UserService userService,
      ApplicationEventPublisher eventPublisher) {
    this.messageRepository = messageRepository;
    this.chatService = chatService;
    this.userService = userService;
    this.eventPublisher = eventPublisher;
  }

  // send a message to a chat you're in (GR8)
  @Transactional
  public MessageDTO send(Long chatId, User user, String text) {
    return send(chatService.getChatFor(chatId, user), user, text);
  }

  // the first message of a DM creates it, or goes into it if it's already there
  @Transactional
  public MessageDTO sendDirect(Long number, User user, String text) {
    User contact = userService.getChatContact(number, user);
    return send(chatService.getOrCreateDirectChat(user, contact), user, text);
  }

  // the open chat gets the message, every member's chat list moves it to the top
  private MessageDTO send(Chat chat, User user, String text) {
    MessageDTO message = MessageDTO.from(messageRepository.save(new Message(chat, user, text)));
    eventPublisher.publishEvent(new ChatEvent(chat.getId(), ChatEventType.MESSAGE_CREATED, message));
    for (User member : chat.getMembers()) {
      eventPublisher.publishEvent(new UserChatEvent(member, ChatEventType.CHAT_UPDATED, message));
    }
    return message;
  }

}
