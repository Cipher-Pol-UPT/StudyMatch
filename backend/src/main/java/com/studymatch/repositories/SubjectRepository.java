package com.studymatch.repositories;

import org.springframework.data.jpa.repository.JpaRepository;

import com.studymatch.models.Subject;

public interface SubjectRepository extends JpaRepository<Subject, Long> {

}
