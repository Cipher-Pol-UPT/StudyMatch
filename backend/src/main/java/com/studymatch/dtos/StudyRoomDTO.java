package com.studymatch.dtos;

import java.time.LocalDateTime;

import com.studymatch.enums.StudyRoomStatus;
import com.studymatch.models.StudyRoom;

public record StudyRoomDTO(Long id, Long subjectId, String subjectName, Long tutorNumber, String tutorName,
    LocalDateTime startsAt, LocalDateTime endsAt, String description, int capacity, String link,
    StudyRoomStatus status, int participantCount) {

  public static StudyRoomDTO from(StudyRoom room) {
    return new StudyRoomDTO(room.getId(), room.getTutorRole().getSubject().getId(),
        room.getTutorRole().getSubject().getName(), room.getTutorRole().getStudent().getNumber(),
        room.getTutorRole().getStudent().getName(), room.getStartsAt(), room.getEndsAt(), room.getDescription(),
        room.getCapacity(), room.getLink(), room.getStatus(), room.getParticipations().size());
  }

}
