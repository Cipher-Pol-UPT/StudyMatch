package com.studymatch.dtos;

import com.studymatch.models.Participation;

// one row of the list the tutor ticks when confirming attendance
public record ParticipantDTO(Long number, String name, boolean attended) {

  public static ParticipantDTO from(Participation participation) {
    return new ParticipantDTO(participation.getStudent().getNumber(), participation.getStudent().getName(),
        participation.isAttended());
  }

}
