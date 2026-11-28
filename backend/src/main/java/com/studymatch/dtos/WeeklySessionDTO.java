package com.studymatch.dtos;

import com.studymatch.enums.RoomType;
import com.studymatch.models.WeeklySession;

import jakarta.validation.constraints.NotNull;

// one session of the weekly plan, the length is checked by the model (INVALID_SESSION_LENGTH)
public record WeeklySessionDTO(
    @NotNull Integer minutes,
    @NotNull RoomType roomType) {

  public static WeeklySessionDTO from(WeeklySession session) {
    return new WeeklySessionDTO(session.getMinutes(), session.getRoomType());
  }

  public WeeklySession toSession() {
    return new WeeklySession(minutes, roomType);
  }

}
