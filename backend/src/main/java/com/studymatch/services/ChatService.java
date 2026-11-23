package com.studymatch.services;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.studymatch.dtos.AddMembersRequestDTO;
import com.studymatch.dtos.ChatContactDTO;
import com.studymatch.dtos.ChatEditedDTO;
import com.studymatch.dtos.ChatSummaryDTO;
import com.studymatch.dtos.CreateGroupChatRequestDTO;
import com.studymatch.dtos.GroupChatPictureRequestDTO;
import com.studymatch.dtos.ImageFileDTO;
import com.studymatch.dtos.RenameGroupChatRequestDTO;
import com.studymatch.dtos.TypingDTO;
import com.studymatch.enums.ChatEventType;
import com.studymatch.events.ChatEvent;
import com.studymatch.events.MemberChatEvent;
import com.studymatch.events.UserChatEvent;
import com.studymatch.exceptions.ErrorCode;
import com.studymatch.exceptions.StudyMatchException;
import com.studymatch.models.Chat;
import com.studymatch.models.CustomChat;
import com.studymatch.models.DirectChat;
import com.studymatch.models.User;
import com.studymatch.repositories.ChatRepository;
import com.studymatch.utils.PresenceTracker;

@Service
public class ChatService {

  private final ChatRepository chatRepository;
  private final UserService userService;
  private final UserBlockService userBlockService;
  private final ChatSettingsService chatSettingsService;
  private final PresenceTracker presenceTracker;
  private final ApplicationEventPublisher eventPublisher;

  public ChatService(ChatRepository chatRepository, UserService userService, UserBlockService userBlockService,
      ChatSettingsService chatSettingsService, PresenceTracker presenceTracker,
      ApplicationEventPublisher eventPublisher) {
    this.chatRepository = chatRepository;
    this.userService = userService;
    this.userBlockService = userBlockService;
    this.chatSettingsService = chatSettingsService;
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

  // who's in it, a group chat in joining order with the owner first. members only like the chat itself (GR8)
  @Transactional(readOnly = true)
  public List<ChatContactDTO> getMembers(Long chatId, User user) {
    return getChatFor(chatId, user).getMembers().stream().map(ChatContactDTO::from).toList();
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
    TypingDTO typing = new TypingDTO(chat.getId(), ChatContactDTO.from(user));
    // a group chat goes to each member like its messages, a removed member's open tab still listens to the topic
    if (chat instanceof CustomChat) {
      for (User member : chat.getMembers()) {
        eventPublisher.publishEvent(new MemberChatEvent(member, chat.getId(), ChatEventType.TYPING, typing));
      }
      return;
    }
    eventPublisher.publishEvent(new ChatEvent(chat.getId(), ChatEventType.TYPING, typing));
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

  // the creator owns it and everyone picked is in straight away, no invitation (GR8).
  // CHAT_ADDED goes to the creator too, for their other tabs
  @Transactional
  public CustomChat createGroupChat(User user, CreateGroupChatRequestDTO request) {
    // counted before the lookup, thousands of numbers would go into one IN (...) and break it
    Set<Long> numbers = new LinkedHashSet<>(request.memberNumbers());
    CustomChat.checkSize(numbers.size() + 1);
    List<User> others = userService.getChatContacts(numbers, user);
    checkNotBlockedBy(others, user);
    CustomChat chat = chatRepository.save(new CustomChat(request.name(), request.picture(), user, others));
    for (User member : chat.getMembers()) {
      eventPublisher.publishEvent(new UserChatEvent(member, ChatEventType.CHAT_ADDED,
          ChatSummaryDTO.from(chat, member, null, 0)));
    }
    return chat;
  }

  // any member adds people, they're in straight away with the whole history, and it starts read (GR8).
  // people already in are skipped, so is the block check for them. gives back who was really added
  @Transactional
  public List<User> addMembers(Long chatId, User user, AddMembersRequestDTO request) {
    CustomChat chat = getLockedCustomChatFor(chatId, user, ErrorCode.CHAT_MEMBERS_FIXED);
    // counted before the lookup like creating, people already in don't count
    Set<Long> numbers = new LinkedHashSet<>(request.memberNumbers());
    chat.getMembers().forEach(member -> numbers.remove(member.getNumber()));
    CustomChat.checkNotTooMany(chat.getMembers().size() + numbers.size());
    List<User> newcomers = userService.getChatContacts(numbers, user);
    checkNotBlockedBy(newcomers, user);
    List<User> added = chat.addMembers(newcomers);
    chatSettingsService.startRead(chat, added);
    return added;
  }

  // only the owner removes people, who lose the chat and its history straight away (GR8). the owner leaves
  // instead (#232), someone who isn't in changes nothing. CHAT_REMOVED takes it off their list
  @Transactional
  public void removeMember(Long chatId, User user, Long number) {
    CustomChat chat = getLockedCustomChatFor(chatId, user, ErrorCode.CHAT_MEMBERS_FIXED);
    if (!chat.isOwner(user)) {
      throw new StudyMatchException(ErrorCode.NOT_CHAT_OWNER);
    }
    if (user.getNumber().equals(number)) {
      throw new StudyMatchException(ErrorCode.CANNOT_REMOVE_YOURSELF);
    }
    chat.getMembers().stream()
        .filter(member -> member.getNumber().equals(number))
        .findFirst()
        .ifPresent(member -> {
          chat.removeMember(member);
          eventPublisher.publishEvent(new UserChatEvent(member, ChatEventType.CHAT_REMOVED, chat.getId()));
        });
  }

  // any member leaves, the owner hands it to whoever's been there longest. CHAT_REMOVED is for their other tabs.
  // gives the chat back so MessageService can delete it if nobody's left (#232)
  @Transactional
  public CustomChat leave(Long chatId, User user) {
    CustomChat chat = getLockedCustomChatFor(chatId, user, ErrorCode.CHAT_MEMBERS_FIXED);
    chat.leave(user);
    eventPublisher.publishEvent(new UserChatEvent(user, ChatEventType.CHAT_REMOVED, chat.getId()));
    return chat;
  }

  // the messages are gone already (MessageService), the settings go here and then the chat
  @Transactional
  public void delete(Chat chat) {
    chatSettingsService.deleteAllOf(chat);
    chatRepository.delete(chat);
  }

  // any member renames a group chat, the name's checked like creating (#234). a DM has no name, so 404
  @Transactional
  public void rename(Long chatId, User user, RenameGroupChatRequestDTO request) {
    CustomChat chat = getLockedCustomChatFor(chatId, user, ErrorCode.NOT_FOUND);
    chat.rename(request.name());
    tellEdited(chat);
  }

  // only the owner changes the picture, null takes it off (#234)
  @Transactional
  public void changePicture(Long chatId, User user, GroupChatPictureRequestDTO request) {
    CustomChat chat = getLockedCustomChatFor(chatId, user, ErrorCode.NOT_FOUND);
    if (!chat.isOwner(user)) {
      throw new StudyMatchException(ErrorCode.NOT_CHAT_OWNER);
    }
    chat.setPicture(request.picture());
    tellEdited(chat);
  }

  // every member's list swaps the name and picture, the editor's other tabs included
  private void tellEdited(CustomChat chat) {
    ChatEditedDTO edited = ChatEditedDTO.from(chat);
    for (User member : chat.getMembers()) {
      eventPublisher.publishEvent(new UserChatEvent(member, ChatEventType.CHAT_EDITED, edited));
    }
  }

  // a group chat someone made, locked so two changes at once go one after the other. it's locked before
  // getChatFor loads the members, or the second would wait and then use the list from before the first.
  // edits lock too, saving writes the whole row back, owner included (#234). anything else gets notGroupChat,
  // a DM's members never change and it has no name. #236: if the assignment chat ends up extending CustomChat
  // it has to be refused here too, its members come from the group
  private CustomChat getLockedCustomChatFor(Long chatId, User user, ErrorCode notGroupChat) {
    chatRepository.findLockedCustomChat(chatId);
    if (getChatFor(chatId, user) instanceof CustomChat chat) {
      return chat;
    }
    throw new StudyMatchException(notGroupChat);
  }

  // nobody who blocked the user can be put in a chat by them, the error names who but not why (GR9)
  private void checkNotBlockedBy(List<User> people, User user) {
    Set<Long> blockers = userBlockService.getBlockerNumbers(user);
    List<Long> refused = people.stream().map(User::getNumber).filter(blockers::contains).toList();
    if (!refused.isEmpty()) {
      throw new StudyMatchException(ErrorCode.CANNOT_ADD_TO_GROUP_CHAT, Map.of("numbers", refused));
    }
  }

  // members only like the chat itself (GR8). a DM has no picture of its own, it uses the contact's photo
  @Transactional(readOnly = true)
  public ImageFileDTO getPicture(Long chatId, User user) {
    if (getChatFor(chatId, user) instanceof CustomChat custom && custom.getPicture() != null) {
      return ImageFileDTO.fromDataUrl(custom.getPicture());
    }
    throw new StudyMatchException(ErrorCode.NOT_FOUND);
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
