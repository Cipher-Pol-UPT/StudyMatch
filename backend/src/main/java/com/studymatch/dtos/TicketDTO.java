package com.studymatch.dtos;

import java.time.LocalDateTime;
import java.util.List;

import com.studymatch.enums.TicketCategory;
import com.studymatch.enums.TicketStatus;
import com.studymatch.models.Ticket;
import com.studymatch.models.TicketReply;

// a ticket with its thread, oldest reply first
public record TicketDTO(Long id, TicketCategory category, String subject, String description, TicketStatus status,
    LocalDateTime createdAt, LocalDateTime updatedAt, List<TicketReplyDTO> replies) {

  public static TicketDTO from(Ticket ticket, List<TicketReply> replies) {
    return new TicketDTO(ticket.getId(), ticket.getCategory(), ticket.getSubject(), ticket.getDescription(),
        ticket.getStatus(), ticket.getCreatedAt(), ticket.getUpdatedAt(),
        replies.stream().map(TicketReplyDTO::from).toList());
  }

}
