package com.studymatch.dtos;

import com.studymatch.enums.TicketCategory;
import com.studymatch.models.Ticket;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record OpenTicketRequestDTO(
    @NotNull TicketCategory category,
    @NotBlank @Size(max = Ticket.MAX_SUBJECT_LENGTH) String subject,
    @NotBlank @Size(max = Ticket.MAX_DESCRIPTION_LENGTH) String description) {
}
