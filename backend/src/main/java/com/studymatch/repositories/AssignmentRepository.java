package com.studymatch.repositories;

import org.springframework.data.jpa.repository.JpaRepository;

import com.studymatch.models.Assignment;

public interface AssignmentRepository extends JpaRepository<Assignment, Long> {

}
