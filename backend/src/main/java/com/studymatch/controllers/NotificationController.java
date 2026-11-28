package com.studymatch.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.studymatch.dtos.NotificationDTO;
import com.studymatch.dtos.PageDTO;
import com.studymatch.exceptions.ErrorCode;
import com.studymatch.exceptions.ErrorCodes;
import com.studymatch.models.User;
import com.studymatch.services.NotificationService;
import com.studymatch.services.UserService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

// every role, and only ever the user's own. a Student without the personality test gets none of it (GR1)
@RestController
@RequestMapping("/notifications/")
@Tag(name = "Notifications", description = "What the System told the user, in the app only")
public class NotificationController {

  private final NotificationService notificationService;
  private final UserService userService;

  public NotificationController(NotificationService notificationService, UserService userService) {
    this.notificationService = notificationService;
    this.userService = userService;
  }

  @GetMapping
  @Operation(summary = "Your notifications, newest first, 20 per page. Each is a type and params to translate, and "
      + "the page it opens if any")
  public ResponseEntity<PageDTO<NotificationDTO>> listNotifications(@RequestParam(defaultValue = "0") int page,
      Authentication authentication) {
    User user = userService.getByInstitutionalEmail(authentication.getName());
    return ResponseEntity.ok(notificationService.listNotifications(user, page));
  }

  // the navbar's badge, so it doesn't load the list on every page
  @GetMapping("unread-count")
  @Operation(summary = "How many of your notifications you haven't read")
  public ResponseEntity<Long> countUnread(Authentication authentication) {
    User user = userService.getByInstitutionalEmail(authentication.getName());
    return ResponseEntity.ok(notificationService.countUnread(user));
  }

  @PutMapping("{id}/read")
  @Operation(summary = "Mark one of your notifications as read, marking it again changes nothing")
  @ErrorCodes(ErrorCode.NOT_FOUND)
  public ResponseEntity<Void> markAsRead(@PathVariable Long id, Authentication authentication) {
    User user = userService.getByInstitutionalEmail(authentication.getName());
    notificationService.markAsRead(id, user);
    return ResponseEntity.noContent().build();
  }

  @PutMapping("read")
  @Operation(summary = "Mark all your notifications as read")
  public ResponseEntity<Void> markAllAsRead(Authentication authentication) {
    User user = userService.getByInstitutionalEmail(authentication.getName());
    notificationService.markAllAsRead(user);
    return ResponseEntity.noContent().build();
  }

}
