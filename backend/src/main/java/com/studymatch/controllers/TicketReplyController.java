package com.studymatch.controllers;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.studymatch.dtos.ReplyTicketRequestDTO;
import com.studymatch.dtos.TicketDTO;
import com.studymatch.exceptions.ErrorCode;
import com.studymatch.exceptions.ErrorCodes;
import com.studymatch.services.TicketReplyService;
import com.studymatch.services.UserService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/tickets/{id}/replies")
@PreAuthorize("hasAnyRole('STUDENT', 'TEACHER', 'STAFF')")
@Tag(name = "Tickets")
public class TicketReplyController {

  private final TicketReplyService ticketReplyService;
  private final UserService userService;

  public TicketReplyController(TicketReplyService ticketReplyService, UserService userService) {
    this.ticketReplyService = ticketReplyService;
    this.userService = userService;
  }

  @PostMapping
  @Operation(summary = "Write back on one of your tickets (up to 2000 characters). A Resolved ticket goes back "
      + "to Open. Answers the ticket with its thread. Someone else's is NOT_FOUND")
  @ErrorCodes(ErrorCode.NOT_FOUND)
  public ResponseEntity<TicketDTO> reply(@PathVariable Long id, @Valid @RequestBody ReplyTicketRequestDTO request,
      Authentication authentication) {
    return ResponseEntity.status(HttpStatus.CREATED).body(
        ticketReplyService.replyToMine(userService.getByInstitutionalEmail(authentication.getName()), id, request));
  }

}
