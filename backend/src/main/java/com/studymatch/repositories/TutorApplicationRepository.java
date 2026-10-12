package com.studymatch.repositories;

import org.springframework.data.jpa.repository.JpaRepository;

import com.studymatch.models.TutorApplication;

public interface TutorApplicationRepository extends JpaRepository<TutorApplication, Long> {

}
