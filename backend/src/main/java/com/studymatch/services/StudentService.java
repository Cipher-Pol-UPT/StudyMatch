package com.studymatch.services;

import org.springframework.stereotype.Service;

import com.studymatch.repositories.StudentRepository;

@Service
public class StudentService {

  private final StudentRepository studentRepository;

  public StudentService(StudentRepository studentRepository) {
    this.studentRepository = studentRepository;
  }

}
