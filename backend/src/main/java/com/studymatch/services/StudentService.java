package com.studymatch.services;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.studymatch.dtos.PersonalityProfileDTO;
import com.studymatch.exceptions.ErrorCode;
import com.studymatch.exceptions.StudyMatchException;
import com.studymatch.models.PersonalityProfile;
import com.studymatch.models.Student;
import com.studymatch.repositories.StudentRepository;

@Service
public class StudentService {

  private final StudentRepository studentRepository;

  public StudentService(StudentRepository studentRepository) {
    this.studentRepository = studentRepository;
  }

  @Transactional
  public PersonalityProfileDTO takePersonalityTest(Long studentNumber, List<Integer> answers) {
    Student student = studentRepository.findById(studentNumber)
        .orElseThrow(() -> new StudyMatchException(ErrorCode.NOT_FOUND));
    // GR1: once per academic year, the reset comes with Start Academic Year (#66)
    if (!student.requiresPersonalityTest()) {
      throw new StudyMatchException(ErrorCode.PERSONALITY_TEST_ALREADY_TAKEN);
    }
    student.setPersonalityProfile(PersonalityProfile.fromAnswers(answers));
    return PersonalityProfileDTO.from(studentRepository.save(student).getPersonalityProfile());
  }

}
