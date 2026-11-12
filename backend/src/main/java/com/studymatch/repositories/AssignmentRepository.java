package com.studymatch.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.studymatch.models.Assignment;
import com.studymatch.models.SubjectEdition;

public interface AssignmentRepository extends JpaRepository<Assignment, Long> {

  List<Assignment> findBySubjectEdition(SubjectEdition subjectEdition);

  boolean existsBySubjectEditionAndNameIgnoreCase(SubjectEdition subjectEdition, String name);

}
