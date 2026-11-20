package com.studymatch.repositories;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.studymatch.models.Participation;

public interface ParticipationRepository extends JpaRepository<Participation, Long> {

  Optional<Participation> findByStudyRoom_IdAndStudent_Number(Long studyRoomId, Long studentNumber);

  // achievements: different students who attended the tutor's rooms that had at least minAttendees
  @Query("""
      select count(distinct p.student) from Participation p
      where p.attended = true
        and p.studyRoom.tutorRole.student.number = :tutorNumber
        and (select count(q) from Participation q where q.studyRoom = p.studyRoom and q.attended = true) >= :minAttendees""")
  long countStudentsTutored(Long tutorNumber, long minAttendees);

}
