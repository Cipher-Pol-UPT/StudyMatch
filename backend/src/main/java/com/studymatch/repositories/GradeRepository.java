package com.studymatch.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.studymatch.models.Assignment;
import com.studymatch.models.Enrolment;
import com.studymatch.models.Grade;

public interface GradeRepository extends JpaRepository<Grade, Long> {

  List<Grade> findByEnrolment_SubjectEditionId(Long subjectEditionId);

  List<Grade> findByEnrolment(Enrolment enrolment);

  Optional<Grade> findByEnrolmentAndAssignment(Enrolment enrolment, Assignment assignment);

}
