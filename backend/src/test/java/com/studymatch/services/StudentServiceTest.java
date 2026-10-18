package com.studymatch.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.studymatch.dtos.PersonalityProfileDTO;
import com.studymatch.exceptions.ErrorCode;
import com.studymatch.exceptions.StudyMatchException;
import com.studymatch.models.Degree;
import com.studymatch.models.PersonalityProfile;
import com.studymatch.models.Student;
import com.studymatch.repositories.StudentRepository;

class StudentServiceTest {

  private static final Long STUDENT_NUMBER = 50001L;
  private static final List<Integer> NEUTRAL_ANSWERS = Collections.nCopies(PersonalityProfile.QUESTION_COUNT, 3);

  private StudentRepository studentRepository;
  private StudentService studentService;
  private Student student;

  @BeforeEach
  void setUp() {
    // fake repository (no db), real service
    studentRepository = mock(StudentRepository.class);
    studentService = new StudentService(studentRepository);
    student = new Student("student@example.com", "Test Student", "hash", new Degree("Test Degree"), 1);
    when(studentRepository.findById(STUDENT_NUMBER)).thenReturn(Optional.of(student));
    when(studentRepository.save(any(Student.class))).thenAnswer(invocation -> invocation.getArgument(0));
  }

  // personality test

  @Test
  @DisplayName("Taking the test saves the scored profile on the student")
  void takePersonalityTestSavesProfile() {
    // Act
    PersonalityProfileDTO result = studentService.takePersonalityTest(STUDENT_NUMBER, NEUTRAL_ANSWERS);

    // Assert
    verify(studentRepository).save(student);
    PersonalityProfile saved = student.getPersonalityProfile();
    assertEquals(new PersonalityProfileDTO(0.5, 0.5, 0.5, 0.5), result);
    assertEquals(PersonalityProfileDTO.from(saved), result);
  }

  @Test
  @DisplayName("Taking the test for an unknown student throws NOT_FOUND")
  void takePersonalityTestUnknownStudent() {
    // Act
    StudyMatchException e = assertThrows(StudyMatchException.class,
        () -> studentService.takePersonalityTest(99999L, NEUTRAL_ANSWERS));

    // Assert
    assertEquals(ErrorCode.NOT_FOUND, e.getCode());
    verify(studentRepository, never()).save(any());
  }

  @Test
  @DisplayName("A student who already has a profile can't retake the test (GR1)")
  void takePersonalityTestTwiceIsRejected() {
    // Arrange
    PersonalityProfile existing = new PersonalityProfile(0.8, 0.6, 0.4, 0.7);
    student.setPersonalityProfile(existing);

    // Act
    StudyMatchException e = assertThrows(StudyMatchException.class,
        () -> studentService.takePersonalityTest(STUDENT_NUMBER, NEUTRAL_ANSWERS));

    // Assert - the old profile is kept untouched
    assertEquals(ErrorCode.PERSONALITY_TEST_ALREADY_TAKEN, e.getCode());
    assertSame(existing, student.getPersonalityProfile());
    verify(studentRepository, never()).save(any());
  }

}
