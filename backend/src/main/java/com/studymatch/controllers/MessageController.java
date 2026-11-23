package com.studymatch.controllers;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.studymatch.dtos.EditMessageRequestDTO;
import com.studymatch.dtos.MessageDTO;
import com.studymatch.dtos.MessagePageDTO;
import com.studymatch.dtos.SendMessageRequestDTO;
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
public class MessageController {

  private final MessageService messageService;
  private final UserService userService;

  public MessageController(MessageService messageService, UserService userService) {
    this.messageService = messageService;
    this.userService = userService;
  }

  // open a chat, then scroll up with the oldest id on screen as before. marking it read is a separate PUT
  @GetMapping("{chatId}/messages")
  @PreAuthorize("hasAnyRole('STUDENT', 'TEACHER')")
  @Operation(summary = "50 messages of a chat you're in, oldest first: the newest ones, or the ones before a message "
      + "id when scrolling up. hasMore is false once the first message is in")
  @ErrorCodes({ ErrorCode.NOT_FOUND })
  public ResponseEntity<MessagePageDTO> getMessages(@PathVariable Long chatId,
      @RequestParam(required = false) Long before, Authentication authentication) {
    User user = userService.getByInstitutionalEmail(authentication.getName());
    return ResponseEntity.ok(messageService.getMessages(chatId, user, before));
  }

  // send a message
  @PostMapping("{chatId}/messages")
  @PreAuthorize("hasAnyRole('STUDENT', 'TEACHER')")
  @Operation(summary = "Send a text message to a chat you're in, pushed to its members. replyToId makes it a reply")
  @ErrorCodes({ ErrorCode.NOT_FOUND, ErrorCode.REPLIED_MESSAGE_NOT_FOUND })
  public ResponseEntity<MessageDTO> send(@PathVariable Long chatId,
      @Valid @RequestBody SendMessageRequestDTO sendRequest, Authentication authentication) {
    User user = userService.getByInstitutionalEmail(authentication.getName());
    return ResponseEntity.status(HttpStatus.CREATED).body(messageService.send(chatId, user, sendRequest));
  }

  // edit your own message, same text rule as sending
  @PutMapping("{chatId}/messages/{messageId}")
  @PreAuthorize("hasAnyRole('STUDENT', 'TEACHER')")
  @Operation(summary = "Change the text of a message you sent, it shows \"(edited)\" and the open chat gets the new "
      + "text. Nobody is notified again")
  @ErrorCodes({ ErrorCode.NOT_FOUND, ErrorCode.NOT_MESSAGE_AUTHOR })
  public ResponseEntity<MessageDTO> edit(@PathVariable Long chatId, @PathVariable Long messageId,
      @Valid @RequestBody EditMessageRequestDTO editRequest, Authentication authentication) {
    User user = userService.getByInstitutionalEmail(authentication.getName());
    return ResponseEntity.ok(messageService.edit(chatId, messageId, user, editRequest.text()));
  }

  // delete your own message, the confirmation is the frontend's
  @DeleteMapping("{chatId}/messages/{messageId}")
  @PreAuthorize("hasAnyRole('STUDENT', 'TEACHER')")
  @Operation(summary = "Delete a message you sent for good, it disappears from the open chat and the unread counts "
      + "go down with it")
  @ErrorCodes({ ErrorCode.NOT_FOUND, ErrorCode.NOT_MESSAGE_AUTHOR })
  public ResponseEntity<Void> delete(@PathVariable Long chatId, @PathVariable Long messageId,
      Authentication authentication) {
    User user = userService.getByInstitutionalEmail(authentication.getName());
    messageService.delete(chatId, messageId, user);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("direct/{number}/messages")
  @PreAuthorize("hasAnyRole('STUDENT', 'TEACHER')")
  @Operation(summary = "Send a DM, the first one creates the chat. Someone you blocked needs unblocking first")
  @ErrorCodes({ ErrorCode.NOT_FOUND, ErrorCode.DIRECT_CHAT_ALREADY_EXISTS, ErrorCode.UNBLOCK_TO_MESSAGE,
      ErrorCode.REPLIED_MESSAGE_NOT_FOUND })
  public ResponseEntity<MessageDTO> sendDirect(@PathVariable Long number,
      @Valid @RequestBody SendMessageRequestDTO sendRequest, Authentication authentication) {
    User user = userService.getByInstitutionalEmail(authentication.getName());
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(messageService.sendDirect(number, user, sendRequest));
  }

}
