package com.studymatch.controllers;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.studymatch.dtos.GuestTicketRequestDTO;
import com.studymatch.dtos.ReplyTicketRequestDTO;
import com.studymatch.dtos.TicketDTO;
import com.studymatch.exceptions.ErrorCode;
import com.studymatch.exceptions.ErrorCodes;
import com.studymatch.services.TicketReplyService;
import com.studymatch.services.TicketService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

// public, for someone who can't log in. the token is the private link from the email (#366)
@RestController
@RequestMapping("/support/tickets/")
@Tag(name = "Tickets")
public class SupportTicketController {

  private final TicketService ticketService;
  private final TicketReplyService ticketReplyService;

  public SupportTicketController(TicketService ticketService, TicketReplyService ticketReplyService) {
    this.ticketService = ticketService;
    this.ticketReplyService = ticketReplyService;
  }

  // the address that connected, X-Forwarded-For is whatever the sender wants it to be
  @PostMapping
  @Operation(summary = "Contact support without logging in: email, category, subject (up to 100 characters) and "
      + "description (up to 2000). The ticket starts Open and the private link goes to the email, never in the "
      + "answer. Up to 3 an hour per email and 10 per ip")
  @ErrorCodes(ErrorCode.TOO_MANY_TICKETS)
  public ResponseEntity<Void> open(@Valid @RequestBody GuestTicketRequestDTO request, HttpServletRequest http) {
    ticketService.openAsGuest(request, http.getRemoteAddr());
    return ResponseEntity.status(HttpStatus.CREATED).build();
  }

  @GetMapping("{token}")
  @Operation(summary = "The ticket behind a private link, with its thread, oldest reply first. A wrong link is "
      + "NOT_FOUND")
  @ErrorCodes(ErrorCode.NOT_FOUND)
  public ResponseEntity<TicketDTO> view(@PathVariable String token) {
    return ResponseEntity.ok(ticketReplyService.viewAsGuest(token));
  }

  @PostMapping("{token}/replies")
  @Operation(summary = "Write back through the private link (up to 2000 characters). A Resolved ticket goes back "
      + "to Open. Answers the ticket with its thread. A wrong link is NOT_FOUND")
  @ErrorCodes(ErrorCode.NOT_FOUND)
  public ResponseEntity<TicketDTO> reply(@PathVariable String token,
      @Valid @RequestBody ReplyTicketRequestDTO request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(ticketReplyService.replyAsGuest(token, request));
  }

}
