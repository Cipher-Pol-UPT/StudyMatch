package com.studymatch.dtos;

import java.util.Set;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// who the tutor ticked, by student number. empty is fine, it means nobody came
public record ConfirmAttendanceRequestDTO(
    @NotNull @Size(max = MAX_ATTENDEES) Set<@NotNull Long> attendeeNumbers) {

  // a room holds 20 at most
  public static final int MAX_ATTENDEES = 20;

}
