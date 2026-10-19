package com.studymatch.services;

import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import com.studymatch.dtos.DegreeRequestDTO;
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

  // "Mathematics" and " mathematics " are the same degree
  public Degree registerDegree(DegreeRequestDTO request) {
    String name = request.name().trim();
    if (degreeRepository.existsByNameIgnoreCase(name)) {
      throw new StudyMatchException(ErrorCode.DEGREE_ALREADY_EXISTS);
    }

    // two Staff registering the same name at once both pass the check above, the unique column stops the second
    try {
      return degreeRepository.save(new Degree(name, request.curricularYears()));
    } catch (DataIntegrityViolationException e) {
      throw new StudyMatchException(ErrorCode.DEGREE_ALREADY_EXISTS);
    }
  }

  public List<Degree> listDegrees() {
    return degreeRepository.findAllByOrderByNameAsc();
  }

  public Degree getDegree(Long id) {
    return degreeRepository.findById(id)
        .orElseThrow(() -> new StudyMatchException(ErrorCode.NOT_FOUND));
  }

}
