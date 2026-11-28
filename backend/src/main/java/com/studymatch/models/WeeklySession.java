package com.studymatch.models;

import com.studymatch.enums.RoomType;
import com.studymatch.exceptions.ErrorCode;
import com.studymatch.exceptions.StudyMatchException;

import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.validation.constraints.NotNull;

// one session a subject has every week, each class section gets it in the timetable (GR13)
@Embeddable
public class WeeklySession {

  // the timetable runs in 30-minute slots, so 60 to 180 minutes is 2 to 6 slots
  public static final int SLOT_MINUTES = 30;
  public static final int MIN_MINUTES = 60;
  public static final int MAX_MINUTES = 180;

  private int minutes;

  @NotNull
  @Enumerated(EnumType.STRING)
  private RoomType roomType;

  protected WeeklySession() {
  }

  public WeeklySession(int minutes, RoomType roomType) {
    if (minutes < MIN_MINUTES || minutes > MAX_MINUTES || minutes % SLOT_MINUTES != 0) {
      throw new StudyMatchException(ErrorCode.INVALID_SESSION_LENGTH);
    }
    this.minutes = minutes;
    this.roomType = roomType;
  }

  public int getMinutes() {
    return minutes;
  }

  public RoomType getRoomType() {
    return roomType;
  }

}
