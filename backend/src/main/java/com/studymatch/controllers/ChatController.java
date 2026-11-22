package com.studymatch.controllers;

import java.security.Principal;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.studymatch.dtos.ChatContactDTO;
import com.studymatch.dtos.ChatSummaryDTO;
import com.studymatch.dtos.DirectChatDTO;
import com.studymatch.exceptions.ErrorCode;
import com.studymatch.exceptions.ErrorCodes;
import com.studymatch.exceptions.StudyMatchException;
import com.studymatch.models.Chat;
import com.studymatch.models.User;
import com.studymatch.services.ChatService;
import com.studymatch.services.MessageService;
import com.studymatch.services.UserBlockService;
import com.studymatch.services.UserService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/chats/")
@Tag(name = "Chats", description = "DMs and group chats, for Students and Teachers. Live updates come through the "
    + "WebSocket at `/ws`")
public class ChatController {

  private final ChatService chatService;
  private final MessageService messageService;
  private final UserService userService;
  private final UserBlockService userBlockService;

  public ChatController(ChatService chatService, MessageService messageService, UserService userService,
      UserBlockService userBlockService) {
    this.chatService = chatService;
    this.messageService = messageService;
    this.userService = userService;
    this.userBlockService = userBlockService;
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('STUDENT', 'TEACHER')")
  @Operation(summary = "Your chats, the latest message first, each with its last message and how many you haven't read")
  public ResponseEntity<List<ChatSummaryDTO>> listChats(Authentication authentication) {
    User user = userService.getByInstitutionalEmail(authentication.getName());
    return ResponseEntity.ok(messageService.listChats(user));
  }

  // the side menu's badge, so it doesn't load the whole list on every page
  @GetMapping("unread-count")
  @PreAuthorize("hasAnyRole('STUDENT', 'TEACHER')")
  @Operation(summary = "How many messages you haven't read, across all your chats")
  public ResponseEntity<Long> countUnread(Authentication authentication) {
    User user = userService.getByInstitutionalEmail(authentication.getName());
    return ResponseEntity.ok(messageService.countUnread(user));
  }

  // the chat list's first load, USER_ONLINE and USER_OFFLINE on the user's queue keep it up to date
  @GetMapping("online")
  @PreAuthorize("hasAnyRole('STUDENT', 'TEACHER')")
  @Operation(summary = "The numbers of the people you share a chat with who have the app open right now")
  public ResponseEntity<List<Long>> getOnlineContacts(Authentication authentication) {
    User user = userService.getByInstitutionalEmail(authentication.getName());
    return ResponseEntity.ok(chatService.getOnlineContacts(user));
  }

  // start a DM: open the one you have, or an empty conversation until the first message
  @GetMapping("direct/{number}")
  @PreAuthorize("hasAnyRole('STUDENT', 'TEACHER')")
  @Operation(summary = "Your DM with a Student or Teacher, chatId is null if you haven't messaged yet or blocked them")
  @ErrorCodes({ ErrorCode.NOT_FOUND })
  public ResponseEntity<DirectChatDTO> getDirectChat(@PathVariable Long number, Authentication authentication) {
    User user = userService.getByInstitutionalEmail(authentication.getName());
    User contact = userService.getChatContact(number, user);
    if (userBlockService.hasBlocked(user, contact)) {
      return ResponseEntity.ok(new DirectChatDTO(null, ChatContactDTO.from(contact), true));
    }
    Long chatId = chatService.findDirectChat(user, contact).map(Chat::getId).orElse(null);
    return ResponseEntity.ok(new DirectChatDTO(chatId, ChatContactDTO.from(contact), false));
  }

  // a websocket frame to /app/chats/{chatId}/typing, not a REST call, so it isn't in swagger
  @MessageMapping("chats/{chatId}/typing")
  public void typing(@DestinationVariable Long chatId, Principal principal) {
    User user = userService.getByInstitutionalEmail(principal.getName());
    chatService.announceTyping(chatId, user);
  }

  // typing in a chat you're not in is just dropped, there's nobody to answer
  @MessageExceptionHandler(StudyMatchException.class)
  public void dropRefusedFrame() {
    // nothing to send back
  }

}
