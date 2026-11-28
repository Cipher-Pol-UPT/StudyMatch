package com.studymatch.controllers;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.studymatch.dtos.OpenTicketRequestDTO;
import com.studymatch.dtos.TicketDTO;
import com.studymatch.dtos.TicketSummaryDTO;
import com.studymatch.exceptions.ErrorCode;
import com.studymatch.exceptions.ErrorCodes;
import com.studymatch.services.TicketReplyService;
import com.studymatch.services.TicketService;
import com.studymatch.services.UserService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

// works before the personality test too, a student stuck on it can still ask for help (GR1)
@RestController
@RequestMapping("/tickets/")
@PreAuthorize("hasAnyRole('STUDENT', 'TEACHER', 'STAFF')")
@Tag(name = "Tickets", description = "Asking the helpdesk for help")
public class TicketController {

  private final TicketService ticketService;
  private final TicketReplyService ticketReplyService;
  private final UserService userService;

  public TicketController(TicketService ticketService, TicketReplyService ticketReplyService,
      UserService userService) {
    this.ticketService = ticketService;
    this.ticketReplyService = ticketReplyService;
    this.userService = userService;
  }

  @PostMapping
  @Operation(summary = "Open a ticket with a category, a subject (up to 100 characters) and a description "
      + "(up to 2000). It starts Open, in the Admin queue for Account and the Staff queue for the rest")
  public ResponseEntity<TicketSummaryDTO> open(@Valid @RequestBody OpenTicketRequestDTO request,
      Authentication authentication) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ticketService.open(userService.getByInstitutionalEmail(authentication.getName()), request));
  }

  @GetMapping("mine")
  @Operation(summary = "Your tickets with their status, the last one replied to first")
  public ResponseEntity<List<TicketSummaryDTO>> listMine(Authentication authentication) {
    return ResponseEntity.ok(ticketService.listMine(myNumber(authentication)));
  }

  @GetMapping("{id}")
  @Operation(summary = "One of your tickets with its thread, oldest reply first. Someone else's is NOT_FOUND")
  @ErrorCodes(ErrorCode.NOT_FOUND)
  public ResponseEntity<TicketDTO> view(@PathVariable Long id, Authentication authentication) {
    return ResponseEntity.ok(ticketReplyService.viewMine(myNumber(authentication), id));
  }

  private Long myNumber(Authentication authentication) {
    return userService.getByInstitutionalEmail(authentication.getName()).getNumber();
  }

}
