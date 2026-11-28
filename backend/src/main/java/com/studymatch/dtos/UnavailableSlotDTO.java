package com.studymatch.dtos;

import java.time.DayOfWeek;
import java.time.LocalTime;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.studymatch.models.UnavailableSlot;

import jakarta.validation.constraints.NotNull;

// in and out, start like "08:30". whether it fits the week grid is checked in UnavailableSlot
public record UnavailableSlotDTO(
    @NotNull DayOfWeek day,
    @NotNull @JsonFormat(pattern = "HH:mm") LocalTime start) {

  public static UnavailableSlotDTO from(UnavailableSlot slot) {
    return new UnavailableSlotDTO(slot.getDay(), slot.getStart());
  }

}
