package com.studymatch.repositories;

import org.springframework.data.jpa.repository.JpaRepository;

import com.studymatch.models.Degree;

public interface DegreeRepository extends JpaRepository<Degree, Long> {

}
