package com.studymatch.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.studymatch.dtos.NotificationLevelDTO;
import com.studymatch.exceptions.ErrorCode;
import com.studymatch.exceptions.ErrorCodes;
import com.studymatch.models.User;
import com.studymatch.services.ChatService;
import com.studymatch.services.ChatSettingsService;
import com.studymatch.services.UserService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/chats/")
@Tag(name = "Chats")
public class ChatSettingsController {

  private final ChatSettingsService chatSettingsService;
  private final ChatService chatService;
  private final UserService userService;

  public ChatSettingsController(ChatSettingsService chatSettingsService, ChatService chatService,
      UserService userService) {
    this.chatSettingsService = chatSettingsService;
    this.chatService = chatService;
    this.userService = userService;
  }

  // called when a chat opens, and again when a message arrives while it's on screen
  @PutMapping("{chatId}/read")
  @PreAuthorize("hasAnyRole('STUDENT', 'TEACHER')")
  @Operation(summary = "Mark a chat you're in as read up to now, your other tabs get CHAT_READ")
  @ErrorCodes({ ErrorCode.NOT_FOUND })
  public ResponseEntity<Void> markRead(@PathVariable Long chatId, Authentication authentication) {
    User user = userService.getByInstitutionalEmail(authentication.getName());
    chatSettingsService.markRead(chatService.getChatFor(chatId, user), user);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("{chatId}/notification-level")
  @PreAuthorize("hasAnyRole('STUDENT', 'TEACHER')")
  @Operation(summary = "How much you're notified of a chat you're in, ALL until you change it")
  @ErrorCodes({ ErrorCode.NOT_FOUND })
  public ResponseEntity<NotificationLevelDTO> getLevel(@PathVariable Long chatId, Authentication authentication) {
    User user = userService.getByInstitutionalEmail(authentication.getName());
    return ResponseEntity.ok(new NotificationLevelDTO(
        chatSettingsService.getLevel(chatService.getChatFor(chatId, user), user)));
  }

  @PutMapping("{chatId}/notification-level")
  @PreAuthorize("hasAnyRole('STUDENT', 'TEACHER')")
  @Operation(summary = "Be notified of every message of a chat you're in, only the ones mentioning you, or none")
  @ErrorCodes({ ErrorCode.NOT_FOUND })
  public ResponseEntity<Void> setLevel(@PathVariable Long chatId, @Valid @RequestBody NotificationLevelDTO levelRequest,
      Authentication authentication) {
    User user = userService.getByInstitutionalEmail(authentication.getName());
    chatSettingsService.setLevel(chatService.getChatFor(chatId, user), user, levelRequest.level());
    return ResponseEntity.noContent().build();
  }

}
