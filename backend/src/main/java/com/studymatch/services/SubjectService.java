package com.studymatch.services;

import org.springframework.stereotype.Service;

import com.studymatch.repositories.SubjectRepository;

@Service
public class SubjectService {

  private final SubjectRepository subjectRepository;

  public SubjectService(SubjectRepository subjectRepository) {
    this.subjectRepository = subjectRepository;
  }

}
