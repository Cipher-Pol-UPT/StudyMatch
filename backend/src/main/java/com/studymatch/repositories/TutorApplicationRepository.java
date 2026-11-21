package com.studymatch.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.studymatch.enums.TutorApplicationStatus;
import com.studymatch.models.TutorApplication;

public interface TutorApplicationRepository extends JpaRepository<TutorApplication, Long> {

  List<TutorApplication> findByStudentNumberAndStatus(Long studentNumber, TutorApplicationStatus status);

  // apply for tutoring (#41): only a Pending one blocks a new application, a Rejected or cancelled one doesn't
  boolean existsByStudentNumberAndSubjectIdAndStatus(Long studentNumber, Long subjectId,
      TutorApplicationStatus status);

}
