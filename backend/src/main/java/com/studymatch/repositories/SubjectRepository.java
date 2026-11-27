package com.studymatch.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.studymatch.models.Subject;

public interface SubjectRepository extends JpaRepository<Subject, Long> {

  Optional<Subject> findByCodeIgnoreCase(String code);

  // open-in-view is off and the response is mapped after the service returns, so the degrees, the
  // degree each placement points at and the category are all loaded here
  @EntityGraph(attributePaths = { "degreeSubjects", "degreeSubjects.degree", "category" })
  List<Subject> findAllByOrderByCodeAsc();

  @EntityGraph(attributePaths = { "degreeSubjects", "degreeSubjects.degree", "category" })
  Optional<Subject> findWithDegreesById(Long id);

}
