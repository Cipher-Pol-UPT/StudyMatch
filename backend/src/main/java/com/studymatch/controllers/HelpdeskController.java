package com.studymatch.controllers;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.studymatch.dtos.QueueDTO;
import com.studymatch.dtos.QueueTicketDTO;
import com.studymatch.dtos.ReplyTicketRequestDTO;
import com.studymatch.dtos.TicketDTO;
import com.studymatch.enums.Role;
import com.studymatch.exceptions.ErrorCode;
import com.studymatch.exceptions.ErrorCodes;
import com.studymatch.services.TicketReplyService;
import com.studymatch.services.TicketService;
import com.studymatch.services.UserService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

// the queue's side of the tickets. staff answer the staff queue, the admin the account one (#364, GR15)
@RestController
@RequestMapping("/helpdesk/tickets/")
@PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
@Tag(name = "Helpdesk", description = "Answering the tickets in your queue")
public class HelpdeskController {

  private final TicketService ticketService;
  private final TicketReplyService ticketReplyService;
  private final UserService userService;

  public HelpdeskController(TicketService ticketService, TicketReplyService ticketReplyService,
      UserService userService) {
    this.ticketService = ticketService;
    this.ticketReplyService = ticketReplyService;
    this.userService = userService;
  }

  @GetMapping
  @Operation(summary = "Your queue, 20 a page: Open first, then In progress, then Resolved, the last one replied "
      + "to first in each. Comes with how many in the queue are Open")
  public ResponseEntity<QueueDTO> listQueue(@RequestParam(defaultValue = "0") int page,
      Authentication authentication) {
    return ResponseEntity.ok(ticketService.listQueue(myRole(authentication), page));
  }

  @GetMapping("{id}")
  @Operation(summary = "A ticket in your queue with its thread, oldest reply first. One from the other queue is "
      + "NOT_FOUND")
  @ErrorCodes(ErrorCode.NOT_FOUND)
  public ResponseEntity<TicketDTO> view(@PathVariable Long id, Authentication authentication) {
    return ResponseEntity.ok(ticketReplyService.viewInQueue(myRole(authentication), id));
  }

  @PostMapping("{id}/replies")
  @Operation(summary = "Answer a ticket in your queue (up to 2000 characters). It goes to In progress and the "
      + "author is notified, a guest by email with a new link. Answers the ticket with its thread")
  @ErrorCodes(ErrorCode.NOT_FOUND)
  public ResponseEntity<TicketDTO> answer(@PathVariable Long id, @Valid @RequestBody ReplyTicketRequestDTO request,
      Authentication authentication) {
    return ResponseEntity.status(HttpStatus.CREATED).body(
        ticketReplyService.answer(userService.getByInstitutionalEmail(authentication.getName()), id, request));
  }

  @PutMapping("{id}/resolve")
  @Operation(summary = "Resolve a ticket in your queue. The author is notified, a guest by email with a new link. "
      + "If they write back it goes back to Open")
  @ErrorCodes({ ErrorCode.NOT_FOUND, ErrorCode.TICKET_ALREADY_RESOLVED })
  public ResponseEntity<QueueTicketDTO> resolve(@PathVariable Long id, Authentication authentication) {
    return ResponseEntity.ok(ticketService.resolve(myRole(authentication), id));
  }

  private Role myRole(Authentication authentication) {
    return userService.getByInstitutionalEmail(authentication.getName()).getRole();
  }

}
