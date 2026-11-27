package com.studymatch.dtos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

class SectionTeacherRequestDTOTest {

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

  @Test
  @DisplayName("A teacher number is valid")
  void teacherNumberIsValid() {
    // Act
    Set<ConstraintViolation<SectionTeacherRequestDTO>> violations = validator
        .validate(new SectionTeacherRequestDTO(3L));

    // Assert
    assertTrue(violations.isEmpty());
  }

  @Test
  @DisplayName("The teacher number can't be missing")
  void missingTeacherNumberIsInvalid() {
    // Act
    Set<String> annotations = validator.validate(new SectionTeacherRequestDTO(null)).stream()
        .map(v -> v.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName())
        .collect(Collectors.toSet());

    // Assert
    assertEquals(Set.of("NotNull"), annotations);
  }

}
