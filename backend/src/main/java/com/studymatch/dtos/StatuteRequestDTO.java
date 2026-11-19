package com.studymatch.dtos;

import java.time.LocalDate;

import com.studymatch.enums.StatuteType;

import jakarta.validation.constraints.NotNull;

// only the type and end date, it starts when it's added (GR7)
public record StatuteRequestDTO(@NotNull StatuteType type, @NotNull LocalDate endDate) {
}
