package com.studymatch.repositories;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.studymatch.models.StudyRoom;

public interface StudyRoomRepository extends JpaRepository<StudyRoom, Long> {

  // rooms the tutor hosts (in any subject) that share part of the [startsAt, endsAt) interval
  @Query("""
      select r from StudyRoom r
      where r.tutorRole.student.number = :tutorNumber
        and r.status <> com.studymatch.enums.StudyRoomStatus.CANCELLED
        and r.startsAt < :endsAt and r.endsAt > :startsAt
      order by r.startsAt""")
  List<StudyRoom> findOverlapping(Long tutorNumber, LocalDateTime startsAt, LocalDateTime endsAt);

}
