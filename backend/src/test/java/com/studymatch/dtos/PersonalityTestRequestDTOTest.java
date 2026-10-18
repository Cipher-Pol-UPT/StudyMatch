package com.studymatch.dtos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.studymatch.models.PersonalityProfile;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

class PersonalityTestRequestDTOTest {

  private static ValidatorFactory validatorFactory;
  private static Validator validator;

  @BeforeAll
  static void createValidator() {
    validatorFactory = Validation.buildDefaultValidatorFactory();
    validator = validatorFactory.getValidator();
  }

  @AfterAll
  static void closeValidator() {
    validatorFactory.close();
  }

  // number of answers

  @Test
  @DisplayName("Exactly 15 answers is valid")
  void fifteenAnswersIsValid() {
    // Act
    Set<ConstraintViolation<PersonalityTestRequestDTO>> violations = validator.validate(withAnswerCount(15));

    // Assert
    assertTrue(violations.isEmpty());
  }

  @Test
  @DisplayName("14 answers is too few")
  void fourteenAnswersIsInvalid() {
    // Act
    Set<ConstraintViolation<PersonalityTestRequestDTO>> violations = validator.validate(withAnswerCount(14));

    // Assert
    assertOnlyViolationOn(violations, "answers");
  }

  @Test
  @DisplayName("16 answers is too many")
  void sixteenAnswersIsInvalid() {
    // Act
    Set<ConstraintViolation<PersonalityTestRequestDTO>> violations = validator.validate(withAnswerCount(16));

    // Assert
    assertOnlyViolationOn(violations, "answers");
  }

  @Test
  @DisplayName("Missing answers are invalid")
  void nullAnswersIsInvalid() {
    // Act
    Set<ConstraintViolation<PersonalityTestRequestDTO>> violations = validator
        .validate(new PersonalityTestRequestDTO(null));

    // Assert
    assertOnlyViolationOn(violations, "answers");
  }

  // answer values

  @ParameterizedTest(name = "{0}")
  @ValueSource(ints = { 1, 5 })
  @DisplayName("Answers at the ends of the scale are valid")
  void answersAtTheEndsAreValid(int value) {
    // Act
    Set<ConstraintViolation<PersonalityTestRequestDTO>> violations = validator.validate(withFirstAnswer(value));

    // Assert
    assertTrue(violations.isEmpty());
  }

  @ParameterizedTest(name = "{0}")
  @ValueSource(ints = { 0, 6 })
  @DisplayName("Answers just outside the scale are invalid")
  void answersOutsideTheScaleAreInvalid(int value) {
    // Act
    Set<ConstraintViolation<PersonalityTestRequestDTO>> violations = validator.validate(withFirstAnswer(value));

    // Assert
    assertOnlyViolationOn(violations, "answers[0].<list element>");
  }

  @Test
  @DisplayName("A blank answer is invalid")
  void nullAnswerIsInvalid() {
    // Act
    Set<ConstraintViolation<PersonalityTestRequestDTO>> violations = validator.validate(withFirstAnswer(null));

    // Assert
    assertOnlyViolationOn(violations, "answers[0].<list element>");
  }

  private static PersonalityTestRequestDTO withAnswerCount(int count) {
    return new PersonalityTestRequestDTO(Collections.nCopies(count, 3));
  }

  private static PersonalityTestRequestDTO withFirstAnswer(Integer value) {
    List<Integer> answers = new ArrayList<>(Collections.nCopies(PersonalityProfile.QUESTION_COUNT, 3));
    answers.set(0, value);
    return new PersonalityTestRequestDTO(answers);
  }

  private static void assertOnlyViolationOn(Set<ConstraintViolation<PersonalityTestRequestDTO>> violations,
      String path) {
    assertEquals(1, violations.size());
    assertEquals(path, violations.iterator().next().getPropertyPath().toString());
  }

}
