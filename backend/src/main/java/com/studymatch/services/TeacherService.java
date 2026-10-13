package com.studymatch.services;

import org.springframework.stereotype.Service;

import com.studymatch.repositories.TeacherRepository;

@Service
public class TeacherService {

  private final TeacherRepository teacherRepository;

  public TeacherService(TeacherRepository teacherRepository) {
    this.teacherRepository = teacherRepository;
  }

}
