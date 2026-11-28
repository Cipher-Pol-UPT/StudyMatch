package com.studymatch.dtos;

import java.time.LocalDate;

import com.studymatch.models.DayOff;

// lastDay is the same as firstDay for one day
public record DayOffResponseDTO(Long id, LocalDate firstDay, LocalDate lastDay, String reason) {

  public static DayOffResponseDTO from(DayOff dayOff) {
    return new DayOffResponseDTO(dayOff.getId(), dayOff.getFirstDay(), dayOff.getLastDay(), dayOff.getReason());
  }

}
