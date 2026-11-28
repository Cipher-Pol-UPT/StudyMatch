package com.studymatch.dtos;

import java.time.LocalDateTime;

import com.studymatch.enums.TicketCategory;
import com.studymatch.enums.TicketStatus;
import com.studymatch.models.Ticket;

// a row of my tickets
public record TicketSummaryDTO(Long id, TicketCategory category, String subject, TicketStatus status,
    LocalDateTime createdAt, LocalDateTime updatedAt) {

  public static TicketSummaryDTO from(Ticket ticket) {
    return new TicketSummaryDTO(ticket.getId(), ticket.getCategory(), ticket.getSubject(), ticket.getStatus(),
        ticket.getCreatedAt(), ticket.getUpdatedAt());
  }

}
