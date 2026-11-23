package com.studymatch.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.studymatch.dtos.ReactRequestDTO;
import com.studymatch.dtos.ReactionDTO;
import com.studymatch.exceptions.ErrorCode;
import com.studymatch.exceptions.ErrorCodes;
import com.studymatch.models.User;
import com.studymatch.services.MessageService;
import com.studymatch.services.UserService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/chats/")
@Tag(name = "Chats")
public class ReactionController {

  private final MessageService messageService;
  private final UserService userService;

  public ReactionController(MessageService messageService, UserService userService) {
    this.messageService = messageService;
    this.userService = userService;
  }

  // react or change it, the double tap is the frontend sending a heart
  @PutMapping("{chatId}/messages/{messageId}/reaction")
  @PreAuthorize("hasAnyRole('STUDENT', 'TEACHER')")
  @Operation(summary = "React to a message with an emoji, it replaces the one you had. The open chat gets it live")
  @ErrorCodes({ ErrorCode.NOT_FOUND, ErrorCode.INVALID_EMOJI })
  public ResponseEntity<ReactionDTO> react(@PathVariable Long chatId, @PathVariable Long messageId,
      @Valid @RequestBody ReactRequestDTO reactRequest, Authentication authentication) {
    User user = userService.getByInstitutionalEmail(authentication.getName());
    return ResponseEntity.ok(messageService.react(chatId, messageId, user, reactRequest.emoji()));
  }

  @DeleteMapping("{chatId}/messages/{messageId}/reaction")
  @PreAuthorize("hasAnyRole('STUDENT', 'TEACHER')")
  @Operation(summary = "Take your reaction off a message. Without one it changes nothing")
  @ErrorCodes({ ErrorCode.NOT_FOUND })
  public ResponseEntity<Void> removeReaction(@PathVariable Long chatId, @PathVariable Long messageId,
      Authentication authentication) {
    User user = userService.getByInstitutionalEmail(authentication.getName());
    messageService.removeReaction(chatId, messageId, user);
    return ResponseEntity.noContent().build();
  }

}
