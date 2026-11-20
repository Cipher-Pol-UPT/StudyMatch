package com.studymatch.repositories;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import com.studymatch.models.StudyRoom;

// the specification part is for View StudyRooms, where any mix of filters can be on or off
public interface StudyRoomRepository extends JpaRepository<StudyRoom, Long>, JpaSpecificationExecutor<StudyRoom> {

  // rooms the tutor hosts (in any subject) that share part of the [startsAt, endsAt) interval
  @Query("""
      select r from StudyRoom r
      where r.tutorRole.student.number = :tutorNumber
        and r.status <> com.studymatch.enums.StudyRoomStatus.CANCELLED
        and r.startsAt < :endsAt and r.endsAt > :startsAt
      order by r.startsAt""")
  List<StudyRoom> findOverlapping(Long tutorNumber, LocalDateTime startsAt, LocalDateTime endsAt);

  // the tutor's rooms that can still be cancelled: open and not started yet
  @Query("""
      select r from StudyRoom r
      where r.tutorRole.student.number = :tutorNumber
        and r.status = com.studymatch.enums.StudyRoomStatus.SCHEDULED
        and r.startsAt > :now""")
  List<StudyRoom> findUpcoming(Long tutorNumber, LocalDateTime now);

}
