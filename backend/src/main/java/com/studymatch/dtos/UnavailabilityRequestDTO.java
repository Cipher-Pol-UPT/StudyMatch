package com.studymatch.dtos;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

// every slot the teacher can't teach, replacing the old ones. empty clears them
public record UnavailabilityRequestDTO(
    @NotNull List<@Valid @NotNull UnavailableSlotDTO> slots) {
}
