package com.studymatch.repositories;

import org.springframework.data.jpa.repository.JpaRepository;

import com.studymatch.models.Absence;

public interface AbsenceRepository extends JpaRepository<Absence, Long> {

}
