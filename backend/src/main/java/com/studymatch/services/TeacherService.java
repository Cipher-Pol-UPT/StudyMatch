package com.studymatch.services;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.studymatch.exceptions.ErrorCode;
import com.studymatch.exceptions.StudyMatchException;
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

  // the same number twice counts once, but every number must be a real teacher
  public Set<Teacher> getTeachers(Collection<Long> numbers) {
    Set<Long> distinctNumbers = new HashSet<>(numbers);
    List<Teacher> teachers = teacherRepository.findAllById(distinctNumbers);
    if (teachers.size() != distinctNumbers.size()) {
      throw new StudyMatchException(ErrorCode.NOT_FOUND);
    }
    return new HashSet<>(teachers);
  }

}
