package com.studymatch.services;

import java.util.List;

import org.springframework.stereotype.Service;

import com.studymatch.models.Teacher;
import com.studymatch.repositories.TeacherRepository;

@Service
public class TeacherService {

  private final TeacherRepository teacherRepository;

  public TeacherService(TeacherRepository teacherRepository) {
    this.teacherRepository = teacherRepository;
  }

  public List<Teacher> listTeachers() {
    return teacherRepository.findAllByOrderByNameAsc();
  }

}
