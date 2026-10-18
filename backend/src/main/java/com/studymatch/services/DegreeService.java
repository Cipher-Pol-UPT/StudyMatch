package com.studymatch.services;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.studymatch.exceptions.ErrorCode;
import com.studymatch.exceptions.StudyMatchException;
import com.studymatch.models.Degree;
import com.studymatch.repositories.DegreeRepository;

@Service
public class DegreeService {

  private final DegreeRepository degreeRepository;

  public DegreeService(DegreeRepository degreeRepository) {
    this.degreeRepository = degreeRepository;
  }

  public List<Degree> findAll() {
    return degreeRepository.findAllByOrderByNameAsc();
  }

  // by name so the form and the CSV column work the same way
  public Optional<Degree> findByName(String name) {
    return degreeRepository.findByNameIgnoreCase(name.trim());
  }

  public Degree getByName(String name) {
    return findByName(name).orElseThrow(() -> new StudyMatchException(ErrorCode.DEGREE_NOT_FOUND));
  }

}
