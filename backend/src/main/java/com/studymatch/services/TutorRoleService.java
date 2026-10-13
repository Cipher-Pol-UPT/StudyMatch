package com.studymatch.services;

import org.springframework.stereotype.Service;

import com.studymatch.repositories.TutorRoleRepository;

@Service
public class TutorRoleService {

  private final TutorRoleRepository tutorRoleRepository;

  public TutorRoleService(TutorRoleRepository tutorRoleRepository) {
    this.tutorRoleRepository = tutorRoleRepository;
  }

}
