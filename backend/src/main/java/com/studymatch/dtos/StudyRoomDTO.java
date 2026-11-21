package com.studymatch.dtos;

import java.time.LocalDateTime;

import com.studymatch.enums.StudyRoomStatus;
import com.studymatch.models.StudyRoom;

public record StudyRoomDTO(Long id, Long subjectId, String subjectName, Long tutorNumber, String tutorName,
    LocalDateTime startsAt, LocalDateTime endsAt, String description, int capacity, String link,
    StudyRoomStatus status, int participantCount, int availablePlaces) {

  public static StudyRoomDTO from(StudyRoom room) {
    return from(room, true);
  }

  // the link is private: only the tutor and the students who joined get it
  public static StudyRoomDTO from(StudyRoom room, boolean showLink) {
    int participants = room.getParticipations().size();
    return new StudyRoomDTO(room.getId(), room.getTutorRole().getSubject().getId(),
        room.getTutorRole().getSubject().getName(), room.getTutorRole().getStudent().getNumber(),
        room.getTutorRole().getStudent().getName(), room.getStartsAt(), room.getEndsAt(), room.getDescription(),
        room.getCapacity(), showLink ? room.getLink() : null, room.getStatus(), participants,
        room.getCapacity() - participants);
  }

}
