package com.studymatch.repositories;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.studymatch.models.Assignment;
import com.studymatch.models.SubjectEdition;

public interface AssignmentRepository extends JpaRepository<Assignment, Long> {

  List<Assignment> findBySubjectEdition(SubjectEdition subjectEdition);

  boolean existsBySubjectEditionAndNameIgnoreCase(SubjectEdition subjectEdition, String name);

  @Query("select coalesce(sum(a.weight), 0) from Assignment a where a.subjectEdition = :subjectEdition")
  BigDecimal sumWeightBySubjectEdition(SubjectEdition subjectEdition);

}
