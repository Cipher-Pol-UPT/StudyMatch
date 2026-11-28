package com.studymatch.dtos;

import java.time.LocalDateTime;

import com.studymatch.enums.Role;
import com.studymatch.models.TicketReply;
import com.studymatch.models.User;

// the author fields are null when the guest who opened the ticket wrote it
public record TicketReplyDTO(Long id, Long authorNumber, String authorName, Role authorRole, String text,
    LocalDateTime createdAt) {

  public static TicketReplyDTO from(TicketReply reply) {
    User author = reply.getAuthor();
    if (author == null) {
      return new TicketReplyDTO(reply.getId(), null, null, null, reply.getText(), reply.getCreatedAt());
    }
    return new TicketReplyDTO(reply.getId(), author.getNumber(), author.getName(), author.getRole(), reply.getText(),
        reply.getCreatedAt());
  }

}
