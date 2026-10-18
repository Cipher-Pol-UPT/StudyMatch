package com.studymatch.services;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.studymatch.dtos.PersonalityProfileDTO;
import com.studymatch.exceptions.ErrorCode;
import com.studymatch.exceptions.StudyMatchException;
import com.studymatch.models.Student;
import com.studymatch.repositories.StudentRepository;

@Service
public class StudentService {

  private final StudentRepository studentRepository;
  private final PersonalityTestService personalityTestService;

  public StudentService(StudentRepository studentRepository, PersonalityTestService personalityTestService) {
    this.studentRepository = studentRepository;
    this.personalityTestService = personalityTestService;
  }

  @Transactional
  public PersonalityProfileDTO takePersonalityTest(Long number, List<Integer> answers) {
    Student student = studentRepository.findById(number)
        .orElseThrow(() -> new StudyMatchException(ErrorCode.NOT_FOUND));
    student.setPersonalityProfile(personalityTestService.score(answers));
    return PersonalityProfileDTO.from(studentRepository.save(student).getPersonalityProfile());
  }

}
