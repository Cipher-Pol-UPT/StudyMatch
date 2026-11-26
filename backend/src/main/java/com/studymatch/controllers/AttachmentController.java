package com.studymatch.controllers;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.studymatch.dtos.AttachmentFileDTO;
import com.studymatch.exceptions.ErrorCode;
import com.studymatch.exceptions.ErrorCodes;
import com.studymatch.models.User;
import com.studymatch.services.MessageService;
import com.studymatch.services.UserService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/chats/")
@Tag(name = "Chats")
public class AttachmentController {

  private final MessageService messageService;
  private final UserService userService;

  public AttachmentController(MessageService messageService, UserService userService) {
    this.messageService = messageService;
    this.userService = userService;
  }

  // private cache like the pictures, so scrolling doesn't fetch every image again. nosniff is on every response
  @GetMapping("{chatId}/messages/{messageId}/attachments/{attachmentId}")
  @PreAuthorize("hasAnyRole('STUDENT', 'TEACHER')")
  @Operation(summary = "A message's file. An image shows, for <img src>, anything else downloads")
  @ErrorCodes(ErrorCode.NOT_FOUND)
  public ResponseEntity<Resource> open(@PathVariable Long chatId, @PathVariable Long messageId,
      @PathVariable Long attachmentId, Authentication authentication) {
    User user = userService.getByInstitutionalEmail(authentication.getName());
    AttachmentFileDTO file = messageService.openAttachment(chatId, messageId, attachmentId, user);
    ContentDisposition.Builder disposition = file.isImage() ? ContentDisposition.inline()
        : ContentDisposition.attachment();
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(file.contentType()))
        .header(HttpHeaders.CONTENT_DISPOSITION,
            disposition.filename(file.name(), StandardCharsets.UTF_8).build().toString())
        .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePrivate())
        .body(file.content());
  }

}
