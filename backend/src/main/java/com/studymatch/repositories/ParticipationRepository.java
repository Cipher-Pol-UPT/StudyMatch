package com.studymatch.repositories;

import org.springframework.data.jpa.repository.JpaRepository;

import com.studymatch.models.Participation;

public interface ParticipationRepository extends JpaRepository<Participation, Long> {

}
