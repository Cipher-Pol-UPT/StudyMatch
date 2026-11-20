package com.studymatch.repositories;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.studymatch.models.Student;

// the specification part is for View Students, where any mix of filters can be on or off
public interface StudentRepository extends JpaRepository<Student, Long>, JpaSpecificationExecutor<Student> {

  // every row shows the degree and the personality profile, so they come in the same query
  @Override
  @EntityGraph(attributePaths = { "degree", "personalityProfile" })
  Page<Student> findAll(Specification<Student> specification, Pageable pageable);

  List<Student> findByPersonalityProfileIsNotNull();

}
