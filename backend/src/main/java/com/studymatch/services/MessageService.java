package com.studymatch.services;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.studymatch.dtos.ChatSummaryDTO;
import com.studymatch.dtos.MessageDTO;
import com.studymatch.dtos.MessagePageDTO;
import com.studymatch.enums.ChatEventType;
import com.studymatch.events.ChatEvent;
import com.studymatch.events.UserChatEvent;
import com.studymatch.exceptions.ErrorCode;
import com.studymatch.exceptions.StudyMatchException;
import com.studymatch.models.Chat;
import com.studymatch.models.Message;
import com.studymatch.models.User;
import com.studymatch.repositories.MessageRepository;
import com.studymatch.repositories.MessageRepository.ChatActivity;

@Service
public class MessageService {

  private static final int MESSAGES_PER_PAGE = 50;

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

  // only the author edits, only the text. the open chat swaps it, the chat list isn't told since an edit notifies
  // no one (GR10). the same text again changes nothing, so no "(edited)" on a message nobody changed
  @Transactional
  public MessageDTO edit(Long chatId, Long messageId, User user, String text) {
    Chat chat = chatService.getChatFor(chatId, user);
    Message message = messageRepository.findByIdAndChat(messageId, chat)
        .orElseThrow(() -> new StudyMatchException(ErrorCode.NOT_FOUND));
    if (!message.getAuthor().getNumber().equals(user.getNumber())) {
      throw new StudyMatchException(ErrorCode.NOT_MESSAGE_AUTHOR);
    }
    if (message.getText().equals(text)) {
      return MessageDTO.from(message);
    }
    message.edit(text);
    MessageDTO edited = MessageDTO.from(message);
    eventPublisher.publishEvent(new ChatEvent(chat.getId(), ChatEventType.MESSAGE_EDITED, edited));
    return edited;
  }

  // an open chat, 50 at a time going back. one extra is fetched to know if there's anything older
  @Transactional(readOnly = true)
  public MessagePageDTO getMessages(Long chatId, User user, Long before) {
    Chat chat = chatService.getChatFor(chatId, user);
    Limit limit = Limit.of(MESSAGES_PER_PAGE + 1);
    List<Message> newestFirst = before == null
        ? messageRepository.findByChatOrderByIdDesc(chat, limit)
        : messageRepository.findByChatAndIdLessThanOrderByIdDesc(chat, before, limit);
    boolean hasMore = newestFirst.size() > MESSAGES_PER_PAGE;
    List<MessageDTO> page = newestFirst.stream().limit(MESSAGES_PER_PAGE).map(MessageDTO::from).toList();
    return new MessagePageDTO(page.reversed(), hasMore);
  }

  // the chat list, the latest message first. here and not in ChatService because every number in a row comes from
  // messages, and a chat always has one since the first message creates it (GR8)
  @Transactional(readOnly = true)
  public List<ChatSummaryDTO> listChats(User user) {
    Map<Long, Chat> chats = chatService.getChatsOf(user).stream()
        .collect(Collectors.toMap(Chat::getId, Function.identity()));
    if (chats.isEmpty()) {
      return List.of();
    }
    return messageRepository.findActivity(chats.values(), user.getNumber()).stream()
        .sorted(Comparator.comparing((ChatActivity activity) -> activity.getLastMessage().getId()).reversed())
        .map(activity -> ChatSummaryDTO.from(chats.get(activity.getLastMessage().getChat().getId()), user,
            MessageDTO.from(activity.getLastMessage()), activity.getUnread()))
        .toList();
  }

  // the side menu's badge, across every chat
  @Transactional(readOnly = true)
  public long countUnread(User user) {
    List<Chat> chats = chatService.getChatsOf(user);
    return chats.isEmpty() ? 0 : messageRepository.countUnread(chats, user.getNumber());
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
