package com.studymatch.dtos;

import java.time.LocalDate;

import com.studymatch.models.DayOff;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// one day, or a range when lastDay is set. how the days fit the year is checked in DayOffService
public record DayOffRequestDTO(
    @NotNull LocalDate firstDay,
    LocalDate lastDay,
    @NotBlank @Size(max = DayOff.REASON_MAX_LENGTH) String reason) {

  public DayOffRequestDTO {
    if (reason != null) {
      reason = reason.trim();
    }
  }

}
