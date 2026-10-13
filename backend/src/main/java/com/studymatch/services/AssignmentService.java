package com.studymatch.services;

import org.springframework.stereotype.Service;

import com.studymatch.repositories.AssignmentRepository;

@Service
public class AssignmentService {

  private final AssignmentRepository assignmentRepository;

  public AssignmentService(AssignmentRepository assignmentRepository) {
    this.assignmentRepository = assignmentRepository;
  }

}
