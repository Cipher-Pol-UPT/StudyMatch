package com.studymatch.dtos;

import com.studymatch.enums.TicketCategory;
import com.studymatch.models.Ticket;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// someone who can't log in, the link to the ticket goes to this email (#366)
public record GuestTicketRequestDTO(
    @NotBlank @Email @Size(max = Ticket.MAX_EMAIL_LENGTH) String email,
    @NotNull TicketCategory category,
    @NotBlank @Size(max = Ticket.MAX_SUBJECT_LENGTH) String subject,
    @NotBlank @Size(max = Ticket.MAX_DESCRIPTION_LENGTH) String description) {

  public GuestTicketRequestDTO {
    if (email != null) {
      email = email.trim();
    }
  }

}
