package com.studymatch.repositories;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import com.studymatch.models.StudyRoom;

// the specification part is for View StudyRooms, where any mix of filters can be on or off
public interface StudyRoomRepository extends JpaRepository<StudyRoom, Long>, JpaSpecificationExecutor<StudyRoom> {

  // one query for the calendar rows instead of one per room for the tutor role, subject, student and participations
  @Override
  @EntityGraph(attributePaths = { "tutorRole.subject", "tutorRole.student", "participations" })
  List<StudyRoom> findAll(Specification<StudyRoom> spec, Sort sort);

  // rooms the tutor hosts (in any subject) that share part of the [startsAt, endsAt) interval
  @Query("""
      select r from StudyRoom r
      where r.tutorRole.student.number = :tutorNumber
        and r.status <> com.studymatch.enums.StudyRoomStatus.CANCELLED
        and r.startsAt < :endsAt and r.endsAt > :startsAt
      order by r.startsAt""")
  List<StudyRoom> findOverlapping(Long tutorNumber, LocalDateTime startsAt, LocalDateTime endsAt);

  // join StudyRoom (#45): the student's other joined rooms that share part of the [startsAt, endsAt) interval
  @Query("""
      select p.studyRoom from Participation p
      where p.student.number = :studentNumber
        and p.studyRoom.id <> :roomId
        and p.studyRoom.status <> com.studymatch.enums.StudyRoomStatus.CANCELLED
        and p.studyRoom.startsAt < :endsAt and p.studyRoom.endsAt > :startsAt
      order by p.studyRoom.startsAt""")
  List<StudyRoom> findJoinedOverlapping(Long studentNumber, Long roomId, LocalDateTime startsAt, LocalDateTime endsAt);

  // the tutor's rooms that can still be cancelled: open and not started yet
  @Query("""
      select r from StudyRoom r
      where r.tutorRole.student.number = :tutorNumber
        and r.status = com.studymatch.enums.StudyRoomStatus.SCHEDULED
        and r.startsAt > :now""")
  List<StudyRoom> findUpcoming(Long tutorNumber, LocalDateTime now);

  // achievements: the tutor's rooms where at least minAttendees were confirmed as attending
  @Query("""
      select count(r) from StudyRoom r
      where r.tutorRole.student.number = :tutorNumber
        and (select count(p) from Participation p where p.studyRoom = r and p.attended = true) >= :minAttendees""")
  long countWithAttendees(Long tutorNumber, long minAttendees);

}
