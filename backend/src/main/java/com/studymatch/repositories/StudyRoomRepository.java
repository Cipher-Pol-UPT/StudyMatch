package com.studymatch.repositories;

import org.springframework.data.jpa.repository.JpaRepository;

import com.studymatch.models.StudyRoom;

public interface StudyRoomRepository extends JpaRepository<StudyRoom, Long> {

}
