package com.studymatch.repositories;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.studymatch.models.Participation;

public interface ParticipationRepository extends JpaRepository<Participation, Long> {

  Optional<Participation> findByStudyRoom_IdAndStudent_Number(Long studyRoomId, Long studentNumber);

}
