package com.studymatch.services;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.studymatch.dtos.AddMembersRequestDTO;
import com.studymatch.dtos.AttachmentFileDTO;
import com.studymatch.dtos.ChatSummaryDTO;
import com.studymatch.dtos.MessageDTO;
import com.studymatch.dtos.MessageDeletedDTO;
import com.studymatch.dtos.MessagePageDTO;
import com.studymatch.dtos.MessageSearchDTO;
import com.studymatch.dtos.ReactionDTO;
import com.studymatch.dtos.SendAttachmentsRequestDTO;
import com.studymatch.dtos.SendMessageRequestDTO;
import com.studymatch.enums.ChatEventType;
import com.studymatch.enums.NotificationLevel;
import com.studymatch.enums.Sticker;
import com.studymatch.events.ChatEvent;
import com.studymatch.events.MemberChatEvent;
import com.studymatch.events.UserChatEvent;
import com.studymatch.exceptions.ErrorCode;
import com.studymatch.exceptions.StudyMatchException;
import com.studymatch.models.Chat;
import com.studymatch.models.CustomChat;
import com.studymatch.models.DirectChat;
import com.studymatch.models.Group;
import com.studymatch.models.GroupChat;
import com.studymatch.models.Message;
import com.studymatch.models.Student;
import com.studymatch.models.User;
import com.studymatch.repositories.MessageRepository;
import com.studymatch.repositories.MessageRepository.ChatActivity;
import com.studymatch.utils.SearchPatterns;

@Service
public class MessageService {

  private static final int MESSAGES_PER_PAGE = 50;
  private static final int MESSAGES_AROUND = 25;
  private static final int RESULTS_PER_PAGE = 25;
  private static final int MIN_SEARCH_LENGTH = 2;

  private final MessageRepository messageRepository;
  private final ChatService chatService;
  private final UserService userService;
  private final UserBlockService userBlockService;
  private final ReactionService reactionService;
  private final AttachmentService attachmentService;
  private final ChatSettingsService chatSettingsService;
  private final NotificationService notificationService;
  private final ApplicationEventPublisher eventPublisher;

  public MessageService(MessageRepository messageRepository, ChatService chatService, UserService userService,
      UserBlockService userBlockService, ReactionService reactionService, AttachmentService attachmentService,
      ChatSettingsService chatSettingsService, NotificationService notificationService,
      ApplicationEventPublisher eventPublisher) {
    this.messageRepository = messageRepository;
    this.chatService = chatService;
    this.userService = userService;
    this.userBlockService = userBlockService;
    this.reactionService = reactionService;
    this.attachmentService = attachmentService;
    this.chatSettingsService = chatSettingsService;
    this.notificationService = notificationService;
    this.eventPublisher = eventPublisher;
  }

  // send a message, a text, a GIF or a sticker, to a chat you're in (GR8)
  @Transactional
  public MessageDTO send(Long chatId, User user, SendMessageRequestDTO request) {
    requireOneContent(request);
    return send(chatService.getChatFor(chatId, user), user, request.text(), request.gifId(), request.sticker(),
        request.replyToId(), List.of());
  }

  // the same with files, the text is optional then (GR10)
  @Transactional
  public MessageDTO sendAttachments(Long chatId, User user, SendAttachmentsRequestDTO request) {
    return send(chatService.getChatFor(chatId, user), user, request.text(), null, null, request.replyToId(),
        request.files());
  }

  // the first message of a DM creates it, or goes into it if it's already there
  @Transactional
  public MessageDTO sendDirect(Long number, User user, SendMessageRequestDTO request) {
    requireOneContent(request);
    return send(getOrCreateDirectChat(number, user), user, request.text(), request.gifId(), request.sticker(),
        request.replyToId(), List.of());
  }

  @Transactional
  public MessageDTO sendDirectAttachments(Long number, User user, SendAttachmentsRequestDTO request) {
    return send(getOrCreateDirectChat(number, user), user, request.text(), null, null, request.replyToId(),
        request.files());
  }

  // only the author edits, only the text, and without attachments it can't be left empty. the open chat swaps it, the chat list swaps the preview if it's the latest
  // without moving the chat, an edit notifies no one (GR10). the same text again changes nothing, so no "(edited)".
  // a message hidden from a blocker stays hidden when edited, it goes the same way as when it was sent (GR9)
  @Transactional
  public MessageDTO edit(Long chatId, Long messageId, User user, String text) {
    Chat chat = chatService.getChatFor(chatId, user);
    // before the message is loaded, or an original deleted meanwhile would still be quoted in what's sent (#218)
    messageRepository.lockOriginalOf(messageId, chat.getId());
    Message message = getOwnMessage(chat, messageId, user);
    // it has no text to change (GR10)
    if (message.isGif() || message.isSticker()) {
      throw new StudyMatchException(ErrorCode.MESSAGE_NOT_EDITABLE);
    }
    if (text.isEmpty() && message.getAttachments().isEmpty()) {
      throw new StudyMatchException(ErrorCode.MESSAGE_EMPTY);
    }
    if (message.getText().equals(text)) {
      return MessageDTO.from(message, user);
    }
    message.edit(text);
    publishToChat(message, ChatEventType.MESSAGE_EDITED, viewer -> MessageDTO.from(message, viewer));
    for (User member : chat.getMembers()) {
      if (!message.isHiddenFrom(member)) {
        eventPublisher.publishEvent(new UserChatEvent(member, ChatEventType.MESSAGE_EDITED,
            MessageDTO.from(message, member)));
      }
    }
    return MessageDTO.from(message, user);
  }

  // only the author, and it's gone for good (GR10). the open chat drops it, and every member's list reloads that
  // chat since its preview and unread count can change. the only message of a DM takes the chat off both lists.
  // a message hidden from a blocker isn't announced to them either, or they'd learn it existed (GR9)
  @Transactional
  public void delete(Long chatId, Long messageId, User user) {
    Chat chat = chatService.getChatFor(chatId, user);
    Message message = getOwnMessage(chat, messageId, user);
    messageRepository.delete(message);
    MessageDeletedDTO deleted = new MessageDeletedDTO(chat.getId(), messageId);
    publishToChat(message, ChatEventType.MESSAGE_DELETED, viewer -> deleted);
    for (User member : chat.getMembers()) {
      if (!message.isHiddenFrom(member)) {
        eventPublisher.publishEvent(new UserChatEvent(member, ChatEventType.MESSAGE_DELETED, deleted));
      }
    }
  }

  // an open chat, 50 at a time: the newest, the ones before one (scrolling up) or the ones after one (scrolling down
  // from a search result). one extra is fetched to know if there's more that way
  @Transactional(readOnly = true)
  public MessagePageDTO getMessages(Long chatId, User user, Long before, Long after) {
    if (before != null && after != null) {
      throw new StudyMatchException(ErrorCode.VALIDATION_FAILED);
    }
    Chat chat = chatService.getChatFor(chatId, user);
    Limit limit = Limit.of(MESSAGES_PER_PAGE + 1);
    if (after != null) {
      List<Message> oldestFirst = messageRepository.findVisibleAfter(chat, after, user.getNumber(), limit);
      return toPage(first(oldestFirst, MESSAGES_PER_PAGE), user, true, oldestFirst.size() > MESSAGES_PER_PAGE);
    }
    List<Message> newestFirst = before == null
        ? messageRepository.findVisible(chat, user.getNumber(), limit)
        : messageRepository.findVisibleBefore(chat, before, user.getNumber(), limit);
    return toPage(first(newestFirst, MESSAGES_PER_PAGE).reversed(), user, newestFirst.size() > MESSAGES_PER_PAGE,
        before != null);
  }

  // jumping to a search result: the message with up to 25 on each side of it
  @Transactional(readOnly = true)
  public MessagePageDTO getMessagesAround(Long chatId, Long messageId, User user) {
    Chat chat = chatService.getChatFor(chatId, user);
    Message message = findVisible(chat, messageId, user);
    Limit limit = Limit.of(MESSAGES_AROUND + 1);
    List<Message> older = messageRepository.findVisibleBefore(chat, messageId, user.getNumber(), limit);
    List<Message> newer = messageRepository.findVisibleAfter(chat, messageId, user.getNumber(), limit);
    List<Message> page = new ArrayList<>(first(older, MESSAGES_AROUND).reversed());
    page.add(message);
    page.addAll(first(newer, MESSAGES_AROUND));
    return toPage(page, user, older.size() > MESSAGES_AROUND, newer.size() > MESSAGES_AROUND);
  }

  // a file of a message you can see, opening its link is the same as seeing the message (GR8, GR9)
  @Transactional(readOnly = true)
  public AttachmentFileDTO openAttachment(Long chatId, Long messageId, Long attachmentId, User user) {
    Chat chat = chatService.getChatFor(chatId, user);
    return attachmentService.open(findVisible(chat, messageId, user), attachmentId);
  }

  // in the chat's texts, newest first, 25 at a time going back from the oldest result
  @Transactional(readOnly = true)
  public MessageSearchDTO search(Long chatId, User user, String text, Long before) {
    Chat chat = chatService.getChatFor(chatId, user);
    String searched = text.strip();
    if (searched.length() < MIN_SEARCH_LENGTH) {
      throw new StudyMatchException(ErrorCode.SEARCH_TOO_SHORT, Map.of("min", MIN_SEARCH_LENGTH));
    }
    List<Message> found = messageRepository.search(chat, SearchPatterns.containing(searched), SearchPatterns.ESCAPE,
        before == null ? Long.MAX_VALUE : before, user.getNumber(), Limit.of(RESULTS_PER_PAGE + 1));
    List<MessageDTO> messages = first(found, RESULTS_PER_PAGE).stream()
        .map(message -> MessageDTO.from(message, user))
        .toList();
    return new MessageSearchDTO(messages, found.size() > RESULTS_PER_PAGE);
  }

  // to any message you can see, your own included. here for the chat and message checks, the rest is in
  // ReactionService. in a DM a blocked user's reaction never reaches the blocker, who doesn't have the DM until
  // they unblock. blocks don't change group chats (GR9)
  @Transactional
  public ReactionDTO react(Long chatId, Long messageId, User user, String emoji) {
    Chat chat = chatService.getChatFor(chatId, user);
    Message message = getVisible(chat, messageId, user)
        .orElseThrow(() -> new StudyMatchException(ErrorCode.NOT_FOUND));
    User hiddenFrom = message.getHiddenFrom();
    if (chat instanceof DirectChat direct && userBlockService.hasBlocked(direct.getOtherUser(user), user)) {
      hiddenFrom = direct.getOtherUser(user);
    }
    return reactionService.react(message, user, emoji, hiddenFrom);
  }

  @Transactional
  public void removeReaction(Long chatId, Long messageId, User user) {
    Chat chat = chatService.getChatFor(chatId, user);
    reactionService.remove(getVisible(chat, messageId, user)
        .orElseThrow(() -> new StudyMatchException(ErrorCode.NOT_FOUND)), user);
  }

  // the chat list, the latest activity first. here and not in ChatService because the numbers in a row come from
  // messages. a new group chat starts empty, a DM without a message the user can see stays out, an empty one
  // would show them the other side wrote while hidden (GR9)
  @Transactional(readOnly = true)
  public List<ChatSummaryDTO> listChats(User user) {
    List<Chat> chats = chatService.getChatsOf(user);
    if (chats.isEmpty()) {
      return List.of();
    }
    Map<Long, ChatActivity> activities = messageRepository.findActivity(chats, user.getNumber()).stream()
        .collect(Collectors.toMap(activity -> activity.getLastMessage().getChat().getId(), Function.identity()));
    return chats.stream()
        .filter(chat -> !(chat instanceof DirectChat) || activities.containsKey(chat.getId()))
        .sorted(Comparator.comparing((Chat chat) -> lastActivity(chat, activities.get(chat.getId())))
            .thenComparing(chat -> lastMessageId(activities.get(chat.getId())))
            .thenComparing(Chat::getId)
            .reversed())
        .map(chat -> toSummary(chat, activities.get(chat.getId()), user))
        .toList();
  }

  // the rules are in ChatService, this is here for the new members' row, which has the last message.
  // nothing is hidden in a group chat and it starts read for them, so one row with 0 unread fits all of them
  @Transactional
  public void addMembers(Long chatId, User user, AddMembersRequestDTO request) {
    List<User> added = chatService.addMembers(chatId, user, request);
    if (added.isEmpty()) {
      return;
    }
    Chat chat = chatService.getChatFor(chatId, user);
    MessageDTO lastMessage = messageRepository.findActivity(List.of(chat), user.getNumber()).stream()
        .findFirst()
        .map(activity -> MessageDTO.from(activity.getLastMessage(), user))
        .orElse(null);
    for (User member : added) {
      eventPublisher.publishEvent(new UserChatEvent(member, ChatEventType.CHAT_ADDED,
          ChatSummaryDTO.from(chat, member, lastMessage, 0)));
    }
  }

  // the rules are in ChatService, this is here because the last one out deletes the chat with its messages
  @Transactional
  public void leave(Long chatId, User user) {
    CustomChat chat = chatService.leave(chatId, user);
    if (chat.getMembers().isEmpty()) {
      messageRepository.deleteByChat(chat);
      chatService.deleteWithSettings(chat);
    }
  }

  // the teacher removed published groups, their chats go with the messages and attachments (#236).
  // here because of the messages, before the groups, the chat points at them
  @Transactional
  public void deleteGroupChats(Collection<Group> groups) {
    List<GroupChat> chats = chatService.getGroupChatsFor(groups);
    if (chats.isEmpty()) {
      return;
    }
    messageRepository.deleteByChatIn(chats);
    chatService.deleteGroupChats(chats);
  }

  // the teacher edited published groups, before is who each one had. whoever left loses the chat, whoever joined
  // gets it with the whole history, here for their row's last message like addMembers (#236)
  @Transactional
  public void groupMembersChanged(Map<Group, List<Student>> before) {
    for (GroupChat chat : chatService.getGroupChatsFor(before.keySet())) {
      List<Student> was = before.get(chat.getGroup());
      List<User> left = was.stream().filter(student -> !chat.hasMember(student)).map(User.class::cast).toList();
      List<User> joined = chat.getMembers().stream()
          .filter(member -> was.stream().noneMatch(student -> student.getNumber().equals(member.getNumber())))
          .toList();
      chatService.groupMembersChanged(chat, left, joined);
      if (joined.isEmpty()) {
        continue;
      }
      Optional<Message> lastMessage = messageRepository.findActivity(List.of(chat), joined.getFirst().getNumber())
          .stream().findFirst().map(ChatActivity::getLastMessage);
      for (User member : joined) {
        eventPublisher.publishEvent(new UserChatEvent(member, ChatEventType.CHAT_ADDED,
            ChatSummaryDTO.from(chat, member, lastMessage.map(message -> MessageDTO.from(message, member))
                .orElse(null), 0)));
      }
    }
  }

  // an empty group chat counts from when it was made
  private static LocalDateTime lastActivity(Chat chat, ChatActivity activity) {
    return activity == null ? chat.getCreatedAt() : activity.getLastMessage().getSentAt();
  }

  // two last messages in the same instant still have different ids, like findActivity picks them.
  // an empty group chat has none, the chat id breaks that tie
  private static long lastMessageId(ChatActivity activity) {
    return activity == null ? 0 : activity.getLastMessage().getId();
  }

  private static ChatSummaryDTO toSummary(Chat chat, ChatActivity activity, User user) {
    if (activity == null) {
      return ChatSummaryDTO.from(chat, user, null, 0);
    }
    return ChatSummaryDTO.from(chat, user, MessageDTO.from(activity.getLastMessage(), user), activity.getUnread());
  }

  // the side menu's badge, across every chat
  @Transactional(readOnly = true)
  public long countUnread(User user) {
    List<Chat> chats = chatService.getChatsOf(user);
    return chats.isEmpty() ? 0 : messageRepository.countUnread(chats, user.getNumber());
  }

  // the blocker has to unblock first, it's their own block so telling them is fine (GR9)
  private DirectChat getOrCreateDirectChat(Long number, User user) {
    User contact = userService.getChatContact(number, user);
    if (userBlockService.hasBlocked(user, contact)) {
      throw new StudyMatchException(ErrorCode.UNBLOCK_TO_MESSAGE);
    }
    return chatService.getOrCreateDirectChat(user, contact);
  }

  // a text, a GIF or a sticker (GR10), before the first message of a DM creates it
  private static void requireOneContent(SendMessageRequestDTO request) {
    if (!request.hasOneContent()) {
      throw new StudyMatchException(ErrorCode.VALIDATION_FAILED);
    }
  }

  // the open chat gets the message, every member's chat list moves it to the top.
  // to someone who blocked the sender it goes through for the sender but never reaches them (GR9)
  private MessageDTO send(Chat chat, User user, String text, String gifId, Sticker sticker, Long replyToId,
      List<MultipartFile> files) {
    chatService.lockForMessage(chat);
    Message original = replyToId == null ? null : getOriginal(chat, replyToId, user);
    Message saved = sticker == null ? new Message(chat, user, text, original, gifId)
        : new Message(chat, user, original, sticker);
    if (chat instanceof DirectChat direct && userBlockService.hasBlocked(direct.getOtherUser(user), user)) {
      saved.hideFrom(direct.getOtherUser(user));
    }
    messageRepository.save(saved);
    attachmentService.attach(saved, files);
    publishToChat(saved, ChatEventType.MESSAGE_CREATED, viewer -> MessageDTO.from(saved, viewer));
    for (User member : chat.getMembers()) {
      if (!saved.isHiddenFrom(member)) {
        eventPublisher.publishEvent(new UserChatEvent(member, ChatEventType.CHAT_UPDATED,
            MessageDTO.from(saved, member)));
      }
    }
    notifyMembers(saved);
    return MessageDTO.from(saved, user);
  }

  // everyone else it reaches, as far as their level lets it: ALL every message, MENTIONS none yet, NONE never.
  // someone who blocked the author isn't told, it doesn't count as unread for them either (GR9, GR11)
  private void notifyMembers(Message message) {
    Chat chat = message.getChat();
    User author = message.getAuthor();
    Map<Long, NotificationLevel> levels = chatSettingsService.getLevels(chat);
    Set<Long> blockers = userBlockService.getBlockerNumbers(author);
    for (User member : chat.getMembers()) {
      if (member.getNumber().equals(author.getNumber()) || message.isHiddenFrom(member)
          || blockers.contains(member.getNumber())
          || levels.getOrDefault(member.getNumber(), NotificationLevel.ALL) != NotificationLevel.ALL) {
        continue;
      }
      notificationService.countChatMessage(member, chat.getId(), chat.getNameFor(member), false);
    }
  }

  // the message being replied to, in this chat and one the user can see. its own code, NOT_FOUND is the chat (#218)
  private Message getOriginal(Chat chat, Long originalId, User user) {
    return getVisible(chat, originalId, user)
        .orElseThrow(() -> new StudyMatchException(ErrorCode.REPLIED_MESSAGE_NOT_FOUND));
  }

  // a message of that chat the user can see, that isn't deleted until what points at it is saved
  private Optional<Message> getVisible(Chat chat, Long messageId, User user) {
    return messageRepository.lockAgainstDelete(messageId, chat.getId())
        .flatMap(id -> messageRepository.findByIdAndChat(id, chat))
        .filter(message -> !message.isHiddenFrom(user));
  }

  // the same just to read it, without keeping it from being deleted
  private Message findVisible(Chat chat, Long messageId, User user) {
    return messageRepository.findByIdAndChat(messageId, chat)
        .filter(found -> !found.isHiddenFrom(user))
        .orElseThrow(() -> new StudyMatchException(ErrorCode.NOT_FOUND));
  }

  // without the extra one that says if there's more
  private static List<Message> first(List<Message> messages, int count) {
    return messages.stream().limit(count).toList();
  }

  private MessagePageDTO toPage(List<Message> oldestFirst, User user, boolean hasMore, boolean hasNewer) {
    List<MessageDTO> messages = oldestFirst.stream().map(message -> MessageDTO.from(message, user)).toList();
    return new MessagePageDTO(messages, reactionService.list(oldestFirst, user), hasMore, hasNewer);
  }

  // a message of that chat you wrote. missing or from another chat is 404, someone else's is 403
  private Message getOwnMessage(Chat chat, Long messageId, User user) {
    Message message = messageRepository.findByIdAndChat(messageId, chat)
        .orElseThrow(() -> new StudyMatchException(ErrorCode.NOT_FOUND));
    if (!message.getAuthor().getNumber().equals(user.getNumber())) {
      throw new StudyMatchException(ErrorCode.NOT_MESSAGE_AUTHOR);
    }
    return message;
  }

  // a hidden message skips the topic, a blocker's tab opened before the block still listens there (GR9).
  // so does a reply quoting a hidden one, only its author may see the quote.
  // a group chat always skips it, a removed member's open tab still listens there too (GR8)
  private void publishToChat(Message message, ChatEventType type, Function<User, Object> payloadFor) {
    Chat chat = message.getChat();
    if (chat instanceof DirectChat && message.getHiddenFrom() == null && !message.quotesHidden()) {
      eventPublisher.publishEvent(new ChatEvent(chat.getId(), type, payloadFor.apply(message.getAuthor())));
      return;
    }
    for (User member : chat.getMembers()) {
      if (!message.isHiddenFrom(member)) {
        eventPublisher.publishEvent(new MemberChatEvent(member, chat.getId(), type, payloadFor.apply(member)));
      }
    }
  }

}
