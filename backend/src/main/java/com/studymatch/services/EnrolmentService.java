package com.studymatch.services;

import org.springframework.stereotype.Service;

import com.studymatch.repositories.EnrolmentRepository;

@Service
public class EnrolmentService {

  private final EnrolmentRepository enrolmentRepository;

  public EnrolmentService(EnrolmentRepository enrolmentRepository) {
    this.enrolmentRepository = enrolmentRepository;
  }

}
