package com.studymatch.services;

import org.springframework.stereotype.Service;

import com.studymatch.repositories.DegreeRepository;

@Service
public class DegreeService {

  private final DegreeRepository degreeRepository;

  public DegreeService(DegreeRepository degreeRepository) {
    this.degreeRepository = degreeRepository;
  }

}
