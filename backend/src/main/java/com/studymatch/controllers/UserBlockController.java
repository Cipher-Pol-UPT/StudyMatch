package com.studymatch.controllers;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.studymatch.dtos.ChatContactDTO;
import com.studymatch.exceptions.ErrorCode;
import com.studymatch.exceptions.ErrorCodes;
import com.studymatch.models.User;
import com.studymatch.services.UserBlockService;
import com.studymatch.services.UserService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

// blocking someone's DMs, not the admin's account block
@RestController
@RequestMapping("/blocks/")
@Tag(name = "Chats")
public class UserBlockController {

  private final UserBlockService userBlockService;
  private final UserService userService;

  public UserBlockController(UserBlockService userBlockService, UserService userService) {
    this.userBlockService = userBlockService;
    this.userService = userService;
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('STUDENT', 'TEACHER')")
  @Operation(summary = "The people you blocked, by name")
  public ResponseEntity<List<ChatContactDTO>> listBlocked(Authentication authentication) {
    User user = userService.getByInstitutionalEmail(authentication.getName());
    return ResponseEntity.ok(userBlockService.listBlocked(user));
  }

  @PutMapping("{number}")
  @PreAuthorize("hasAnyRole('STUDENT', 'TEACHER')")
  @Operation(summary = "Block a Student or Teacher: your DM with them is hidden and their new DMs never reach you. "
      + "They aren't told, and blocking twice changes nothing")
  @ErrorCodes({ ErrorCode.NOT_FOUND, ErrorCode.CANNOT_BLOCK_YOURSELF })
  public ResponseEntity<Void> block(@PathVariable Long number, Authentication authentication) {
    User user = userService.getByInstitutionalEmail(authentication.getName());
    userBlockService.block(user, number);
    return ResponseEntity.noContent().build();
  }

  @DeleteMapping("{number}")
  @PreAuthorize("hasAnyRole('STUDENT', 'TEACHER')")
  @Operation(summary = "Unblock someone, the DM comes back without what they sent while blocked")
  @ErrorCodes({ ErrorCode.NOT_FOUND, ErrorCode.CANNOT_BLOCK_YOURSELF })
  public ResponseEntity<Void> unblock(@PathVariable Long number, Authentication authentication) {
    User user = userService.getByInstitutionalEmail(authentication.getName());
    userBlockService.unblock(user, number);
    return ResponseEntity.noContent().build();
  }

}
