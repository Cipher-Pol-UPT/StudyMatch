package com.studymatch.services;

import org.springframework.stereotype.Service;

import com.studymatch.repositories.GradeRepository;

@Service
public class GradeService {

  private final GradeRepository gradeRepository;

  public GradeService(GradeRepository gradeRepository) {
    this.gradeRepository = gradeRepository;
  }

}
