package com.studymatch.dtos;

import java.time.LocalDateTime;

import com.studymatch.enums.StudyRoomStatus;
import com.studymatch.models.StudyRoom;

// the calendar entry plus the tutor's rating in that subject and what the student who opened it can do
public record StudyRoomDetailDTO(Long id, Long subjectId, String subjectName, Long tutorNumber, String tutorName,
    LocalDateTime startsAt, LocalDateTime endsAt, String description, int capacity, String link,
    StudyRoomStatus status, int participantCount, int availablePlaces, Double tutorAverageRating, long tutorRatingCount,
    boolean hosted, boolean joined, boolean canJoin) {

  public static StudyRoomDetailDTO from(StudyRoom room, Double tutorAverageRating, long tutorRatingCount,
      boolean hosted, boolean joined, boolean canJoin) {
    int participants = room.getParticipations().size();
    return new StudyRoomDetailDTO(room.getId(), room.getTutorRole().getSubject().getId(),
        room.getTutorRole().getSubject().getName(), room.getTutorRole().getStudent().getNumber(),
        room.getTutorRole().getStudent().getName(), room.getStartsAt(), room.getEndsAt(), room.getDescription(),
        room.getCapacity(), room.getLink(), room.getStatus(), participants, room.getCapacity() - participants,
        tutorAverageRating, tutorRatingCount, hosted, joined, canJoin);
  }

}
