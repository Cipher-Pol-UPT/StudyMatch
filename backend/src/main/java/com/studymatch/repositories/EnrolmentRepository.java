package com.studymatch.repositories;

import org.springframework.data.jpa.repository.JpaRepository;

import com.studymatch.models.Enrolment;

public interface EnrolmentRepository extends JpaRepository<Enrolment, Long> {

}
