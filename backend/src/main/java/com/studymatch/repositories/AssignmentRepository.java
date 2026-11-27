package com.studymatch.repositories;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import com.studymatch.models.Assignment;
import com.studymatch.models.SubjectEdition;

import jakarta.persistence.LockModeType;

public interface AssignmentRepository extends JpaRepository<Assignment, Long> {

  List<Assignment> findBySubjectEdition(SubjectEdition subjectEdition);

  Optional<Assignment> findByIdAndSubjectEditionId(Long id, Long subjectEditionId);

  // generate groups: two regenerations at once would clash on the draft numbers, so the second one waits
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<Assignment> findLockedByIdAndSubjectEditionId(Long id, Long subjectEditionId);

  boolean existsBySubjectEditionAndNameIgnoreCase(SubjectEdition subjectEdition, String name);

  long countBySubjectEdition(SubjectEdition subjectEdition);

  @Query("select coalesce(sum(a.weight), 0) from Assignment a where a.subjectEdition = :subjectEdition")
  BigDecimal sumWeightBySubjectEdition(SubjectEdition subjectEdition);

}
