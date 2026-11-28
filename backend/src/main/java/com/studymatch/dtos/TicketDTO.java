package com.studymatch.dtos;

import java.time.LocalDateTime;
import java.util.List;

import com.studymatch.enums.TicketCategory;
import com.studymatch.enums.TicketStatus;
import com.studymatch.models.Ticket;
import com.studymatch.models.TicketReply;
import com.studymatch.models.User;

// a ticket with its thread, oldest reply first. the queue needs who asked (#364): an author, or a guest's email
public record TicketDTO(Long id, Long authorNumber, String authorName, String guestEmail, TicketCategory category,
    String subject, String description, TicketStatus status, LocalDateTime createdAt, LocalDateTime updatedAt,
    List<TicketReplyDTO> replies) {

  public static TicketDTO from(Ticket ticket, List<TicketReply> replies) {
    User author = ticket.getAuthor();
    return new TicketDTO(ticket.getId(), author == null ? null : author.getNumber(),
        author == null ? null : author.getName(), ticket.getGuestEmail(), ticket.getCategory(), ticket.getSubject(),
        ticket.getDescription(), ticket.getStatus(), ticket.getCreatedAt(), ticket.getUpdatedAt(),
        replies.stream().map(TicketReplyDTO::from).toList());
  }

}
