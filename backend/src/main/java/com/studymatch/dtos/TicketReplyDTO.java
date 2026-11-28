package com.studymatch.dtos;

import java.time.LocalDateTime;

import com.studymatch.enums.Role;
import com.studymatch.models.TicketReply;
import com.studymatch.models.User;

public record TicketReplyDTO(Long id, Long authorNumber, String authorName, Role authorRole, String text,
    LocalDateTime createdAt) {

  public static TicketReplyDTO from(TicketReply reply) {
    User author = reply.getAuthor();
    return new TicketReplyDTO(reply.getId(), author.getNumber(), author.getName(), author.getRole(), reply.getText(),
        reply.getCreatedAt());
  }

}
