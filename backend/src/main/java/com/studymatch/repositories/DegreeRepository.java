package com.studymatch.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.studymatch.models.Degree;

public interface DegreeRepository extends JpaRepository<Degree, Long> {

  boolean existsByNameIgnoreCase(String name);

  List<Degree> findAllByOrderByNameAsc();

}
