package com.studymatch.dtos;

import com.studymatch.models.TicketReply;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReplyTicketRequestDTO(@NotBlank @Size(max = TicketReply.MAX_TEXT_LENGTH) String text) {
}
