package com.studymatch.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.studymatch.models.Subject;

public interface SubjectRepository extends JpaRepository<Subject, Long> {

  boolean existsByCodeIgnoreCase(String code);

  // open-in-view is off, so the degrees are loaded here for the response to read them
  @EntityGraph(attributePaths = "degreeSubjects")
  List<Subject> findAllByOrderByCodeAsc();

  @EntityGraph(attributePaths = "degreeSubjects")
  Optional<Subject> findWithDegreesById(Long id);

}
