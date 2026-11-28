package com.studymatch.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.studymatch.models.Subject;

public interface SubjectRepository extends JpaRepository<Subject, Long> {

  Optional<Subject> findByCodeIgnoreCase(String code);

  // open-in-view is off, so the degrees are loaded here for the response to read them.
  // The graph leaves what it doesn't name lazy, so the category and each placement's degree are named too
  @EntityGraph(attributePaths = { "category", "degreeSubjects", "degreeSubjects.degree" })
  List<Subject> findAllByOrderByCodeAsc();

  @EntityGraph(attributePaths = { "category", "degreeSubjects", "degreeSubjects.degree" })
  Optional<Subject> findWithDegreesById(Long id);

}
