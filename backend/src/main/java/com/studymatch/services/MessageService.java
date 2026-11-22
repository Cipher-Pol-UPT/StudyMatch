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
import com.studymatch.events.MemberChatEvent;
import com.studymatch.events.UserChatEvent;
import com.studymatch.exceptions.ErrorCode;
import com.studymatch.exceptions.StudyMatchException;
import com.studymatch.models.Chat;
import com.studymatch.models.DirectChat;
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
  private final UserBlockService userBlockService;
  private final ApplicationEventPublisher eventPublisher;

  public MessageService(MessageRepository messageRepository, ChatService chatService, UserService userService,
      UserBlockService userBlockService, ApplicationEventPublisher eventPublisher) {
    this.messageRepository = messageRepository;
    this.chatService = chatService;
    this.userService = userService;
    this.userBlockService = userBlockService;
    this.eventPublisher = eventPublisher;
  }

  // send a message to a chat you're in (GR8)
  @Transactional
  public MessageDTO send(Long chatId, User user, String text) {
    return send(chatService.getChatFor(chatId, user), user, text);
  }

  // the first message of a DM creates it, or goes into it if it's already there.
  // the blocker has to unblock first, it's their own block so telling them is fine (GR9)
  @Transactional
  public MessageDTO sendDirect(Long number, User user, String text) {
    User contact = userService.getChatContact(number, user);
    if (userBlockService.hasBlocked(user, contact)) {
      throw new StudyMatchException(ErrorCode.UNBLOCK_TO_MESSAGE);
    }
    return send(chatService.getOrCreateDirectChat(user, contact), user, text);
  }

  // only the author edits, only the text. the open chat swaps it, the chat list swaps the preview if it's the latest
  // without moving the chat, an edit notifies no one (GR10). the same text again changes nothing, so no "(edited)".
  // a message hidden from a blocker stays hidden when edited, it goes the same way as when it was sent (GR9)
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
    publishToChat(message, ChatEventType.MESSAGE_EDITED, edited);
    for (User member : chat.getMembers()) {
      if (!message.isHiddenFrom(member)) {
        eventPublisher.publishEvent(new UserChatEvent(member, ChatEventType.MESSAGE_EDITED, edited));
      }
    }
    return edited;
  }

  // an open chat, 50 at a time going back. one extra is fetched to know if there's anything older
  @Transactional(readOnly = true)
  public MessagePageDTO getMessages(Long chatId, User user, Long before) {
    Chat chat = chatService.getChatFor(chatId, user);
    Limit limit = Limit.of(MESSAGES_PER_PAGE + 1);
    List<Message> newestFirst = before == null
        ? messageRepository.findVisible(chat, user.getNumber(), limit)
        : messageRepository.findVisibleBefore(chat, before, user.getNumber(), limit);
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

  // the open chat gets the message, every member's chat list moves it to the top.
  // to someone who blocked the sender it goes through for the sender but never reaches them (GR9)
  private MessageDTO send(Chat chat, User user, String text) {
    Message saved = new Message(chat, user, text);
    if (chat instanceof DirectChat direct && userBlockService.hasBlocked(direct.getOtherUser(user), user)) {
      saved.hideFrom(direct.getOtherUser(user));
    }
    MessageDTO message = MessageDTO.from(messageRepository.save(saved));
    publishToChat(saved, ChatEventType.MESSAGE_CREATED, message);
    for (User member : chat.getMembers()) {
      if (!saved.isHiddenFrom(member)) {
        eventPublisher.publishEvent(new UserChatEvent(member, ChatEventType.CHAT_UPDATED, message));
      }
    }
    return message;
  }

  // a hidden message skips the topic, a blocker's tab opened before the block still listens there (GR9)
  private void publishToChat(Message message, ChatEventType type, Object payload) {
    Chat chat = message.getChat();
    if (message.getHiddenFrom() == null) {
      eventPublisher.publishEvent(new ChatEvent(chat.getId(), type, payload));
      return;
    }
    for (User member : chat.getMembers()) {
      if (!message.isHiddenFrom(member)) {
        eventPublisher.publishEvent(new MemberChatEvent(member, chat.getId(), type, payload));
      }
    }
  }

}
