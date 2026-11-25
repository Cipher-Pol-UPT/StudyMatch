package com.studymatch.controllers;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
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
import com.studymatch.dtos.MessageSearchDTO;
import com.studymatch.dtos.SendAttachmentsRequestDTO;
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

  // swagger shows the JSON and the form as one endpoint, so they share what it says
  private static final String SEND = "Send a message to a chat you're in, pushed to its members. replyToId makes it a "
      + "reply. As JSON it's only text. As a form it has up to 5 files of 10 MB and the text is optional, and the file "
      + "errors say which one it was, by name and index";
  private static final String SEND_DIRECT = "Send a DM, the first one creates the chat. Someone you blocked needs "
      + "unblocking first. As JSON or as a form with files, like in a chat";

  private final MessageService messageService;
  private final UserService userService;

  public MessageController(MessageService messageService, UserService userService) {
    this.messageService = messageService;
    this.userService = userService;
  }

  // open a chat, then scroll up with the oldest id on screen as before, or down from a search result with the newest
  // as after. marking it read is a separate PUT
  @GetMapping("{chatId}/messages")
  @PreAuthorize("hasAnyRole('STUDENT', 'TEACHER')")
  @Operation(summary = "50 messages of a chat you're in, oldest first: the newest ones, or the ones before or after a "
      + "message id when scrolling. hasMore is false once the first message is in, hasNewer once the latest is")
  @ErrorCodes({ ErrorCode.NOT_FOUND })
  public ResponseEntity<MessagePageDTO> getMessages(@PathVariable Long chatId,
      @RequestParam(required = false) Long before, @RequestParam(required = false) Long after,
      Authentication authentication) {
    User user = userService.getByInstitutionalEmail(authentication.getName());
    return ResponseEntity.ok(messageService.getMessages(chatId, user, before, after));
  }

  // the chat's search, and jumping to a result
  @GetMapping("{chatId}/messages/search")
  @PreAuthorize("hasAnyRole('STUDENT', 'TEACHER')")
  @Operation(summary = "The messages of a chat you're in with that text, in any capitals, newest first, 25 at a time. "
      + "before is the oldest result's id")
  @ErrorCodes({ ErrorCode.NOT_FOUND, ErrorCode.SEARCH_TOO_SHORT })
  public ResponseEntity<MessageSearchDTO> search(@PathVariable Long chatId, @RequestParam String text,
      @RequestParam(required = false) Long before, Authentication authentication) {
    User user = userService.getByInstitutionalEmail(authentication.getName());
    return ResponseEntity.ok(messageService.search(chatId, user, text, before));
  }

  @GetMapping("{chatId}/messages/{messageId}/around")
  @PreAuthorize("hasAnyRole('STUDENT', 'TEACHER')")
  @Operation(summary = "A message with up to 25 on each side of it, oldest first, like a page of the chat")
  @ErrorCodes({ ErrorCode.NOT_FOUND })
  public ResponseEntity<MessagePageDTO> getMessagesAround(@PathVariable Long chatId, @PathVariable Long messageId,
      Authentication authentication) {
    User user = userService.getByInstitutionalEmail(authentication.getName());
    return ResponseEntity.ok(messageService.getMessagesAround(chatId, messageId, user));
  }

  // send a message
  @PostMapping("{chatId}/messages")
  @PreAuthorize("hasAnyRole('STUDENT', 'TEACHER')")
  @Operation(summary = SEND)
  @ErrorCodes({ ErrorCode.NOT_FOUND, ErrorCode.REPLIED_MESSAGE_NOT_FOUND })
  public ResponseEntity<MessageDTO> send(@PathVariable Long chatId,
      @Valid @RequestBody SendMessageRequestDTO sendRequest, Authentication authentication) {
    User user = userService.getByInstitutionalEmail(authentication.getName());
    return ResponseEntity.status(HttpStatus.CREATED).body(messageService.send(chatId, user, sendRequest));
  }

  // the same url with a form instead of JSON
  @PostMapping(value = "{chatId}/messages", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @PreAuthorize("hasAnyRole('STUDENT', 'TEACHER')")
  @Operation(summary = SEND)
  @ErrorCodes({ ErrorCode.NOT_FOUND, ErrorCode.REPLIED_MESSAGE_NOT_FOUND, ErrorCode.TOO_MANY_ATTACHMENTS,
      ErrorCode.ATTACHMENT_TYPE_NOT_ALLOWED, ErrorCode.ATTACHMENT_TOO_LARGE, ErrorCode.ATTACHMENT_CONTENT_MISMATCH,
      ErrorCode.FILE_TOO_LARGE })
  public ResponseEntity<MessageDTO> sendAttachments(@PathVariable Long chatId,
      @Valid @ModelAttribute SendAttachmentsRequestDTO sendRequest, Authentication authentication) {
    User user = userService.getByInstitutionalEmail(authentication.getName());
    return ResponseEntity.status(HttpStatus.CREATED).body(messageService.sendAttachments(chatId, user, sendRequest));
  }

  // edit your own message, same text rule as sending
  @PutMapping("{chatId}/messages/{messageId}")
  @PreAuthorize("hasAnyRole('STUDENT', 'TEACHER')")
  @Operation(summary = "Change the text of a message you sent, it shows \"(edited)\" and the open chat gets the new "
      + "text. Nobody is notified again. It can only be left empty if it has attachments")
  @ErrorCodes({ ErrorCode.NOT_FOUND, ErrorCode.NOT_MESSAGE_AUTHOR, ErrorCode.MESSAGE_EMPTY })
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
  @Operation(summary = SEND_DIRECT)
  @ErrorCodes({ ErrorCode.NOT_FOUND, ErrorCode.DIRECT_CHAT_ALREADY_EXISTS, ErrorCode.UNBLOCK_TO_MESSAGE,
      ErrorCode.REPLIED_MESSAGE_NOT_FOUND })
  public ResponseEntity<MessageDTO> sendDirect(@PathVariable Long number,
      @Valid @RequestBody SendMessageRequestDTO sendRequest, Authentication authentication) {
    User user = userService.getByInstitutionalEmail(authentication.getName());
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(messageService.sendDirect(number, user, sendRequest));
  }

  @PostMapping(value = "direct/{number}/messages", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @PreAuthorize("hasAnyRole('STUDENT', 'TEACHER')")
  @Operation(summary = SEND_DIRECT)
  @ErrorCodes({ ErrorCode.NOT_FOUND, ErrorCode.DIRECT_CHAT_ALREADY_EXISTS, ErrorCode.UNBLOCK_TO_MESSAGE,
      ErrorCode.REPLIED_MESSAGE_NOT_FOUND, ErrorCode.TOO_MANY_ATTACHMENTS, ErrorCode.ATTACHMENT_TYPE_NOT_ALLOWED,
      ErrorCode.ATTACHMENT_TOO_LARGE, ErrorCode.ATTACHMENT_CONTENT_MISMATCH, ErrorCode.FILE_TOO_LARGE })
  public ResponseEntity<MessageDTO> sendDirectAttachments(@PathVariable Long number,
      @Valid @ModelAttribute SendAttachmentsRequestDTO sendRequest, Authentication authentication) {
    User user = userService.getByInstitutionalEmail(authentication.getName());
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(messageService.sendDirectAttachments(number, user, sendRequest));
  }

}
