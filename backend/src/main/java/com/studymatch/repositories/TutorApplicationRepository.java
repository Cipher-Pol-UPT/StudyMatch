package com.studymatch.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.studymatch.enums.TutorApplicationStatus;
import com.studymatch.models.TutorApplication;

public interface TutorApplicationRepository extends JpaRepository<TutorApplication, Long> {

  List<TutorApplication> findByStudentNumberAndStatus(Long studentNumber, TutorApplicationStatus status);

  // apply for tutoring (#41): the student's application for a subject, so a pending one or a role can block a new one
  Optional<TutorApplication> findByStudentNumberAndSubjectId(Long studentNumber, Long subjectId);

}
