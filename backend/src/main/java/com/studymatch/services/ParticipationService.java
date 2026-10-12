package com.studymatch.services;

import org.springframework.stereotype.Service;

import com.studymatch.repositories.ParticipationRepository;

@Service
public class ParticipationService {

  private final ParticipationRepository participationRepository;

  public ParticipationService(ParticipationRepository participationRepository) {
    this.participationRepository = participationRepository;
  }

}
