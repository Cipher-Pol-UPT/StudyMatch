package com.studymatch.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.studymatch.models.Degree;

public interface DegreeRepository extends JpaRepository<Degree, Long> {

  Optional<Degree> findByNameIgnoreCase(String name);

  List<Degree> findAllByOrderByNameAsc();

}
