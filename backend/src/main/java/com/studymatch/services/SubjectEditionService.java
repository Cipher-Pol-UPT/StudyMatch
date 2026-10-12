package com.studymatch.services;

import org.springframework.stereotype.Service;

import com.studymatch.repositories.SubjectEditionRepository;

@Service
public class SubjectEditionService {

  private final SubjectEditionRepository subjectEditionRepository;

  public SubjectEditionService(SubjectEditionRepository subjectEditionRepository) {
    this.subjectEditionRepository = subjectEditionRepository;
  }

}
