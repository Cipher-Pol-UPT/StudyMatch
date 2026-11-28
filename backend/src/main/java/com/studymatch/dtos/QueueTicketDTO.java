package com.studymatch.dtos;

import java.time.LocalDateTime;

import com.studymatch.enums.TicketCategory;
import com.studymatch.enums.TicketStatus;
import com.studymatch.models.Ticket;
import com.studymatch.models.User;

// a row of a helpdesk queue, with who asked: an author, or a guest's email (#364)
public record QueueTicketDTO(Long id, Long authorNumber, String authorName, String guestEmail,
    TicketCategory category, String subject, TicketStatus status, LocalDateTime createdAt, LocalDateTime updatedAt) {

  public static QueueTicketDTO from(Ticket ticket) {
    User author = ticket.getAuthor();
    return new QueueTicketDTO(ticket.getId(), author == null ? null : author.getNumber(),
        author == null ? null : author.getName(), ticket.getGuestEmail(), ticket.getCategory(), ticket.getSubject(),
        ticket.getStatus(), ticket.getCreatedAt(), ticket.getUpdatedAt());
  }

}
