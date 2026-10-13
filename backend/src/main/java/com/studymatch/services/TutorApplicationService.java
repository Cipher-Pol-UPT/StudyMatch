package com.studymatch.services;

import org.springframework.stereotype.Service;

import com.studymatch.repositories.TutorApplicationRepository;

@Service
public class TutorApplicationService {

  private final TutorApplicationRepository tutorApplicationRepository;

  public TutorApplicationService(TutorApplicationRepository tutorApplicationRepository) {
    this.tutorApplicationRepository = tutorApplicationRepository;
  }

}
