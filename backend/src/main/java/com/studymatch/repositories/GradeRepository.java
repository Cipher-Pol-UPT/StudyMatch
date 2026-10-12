package com.studymatch.repositories;

import org.springframework.data.jpa.repository.JpaRepository;

import com.studymatch.models.Grade;

public interface GradeRepository extends JpaRepository<Grade, Long> {

}
