package com.studymatch.dtos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

class BlockAccountRequestDTOTest {

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

  private Set<ConstraintViolation<BlockAccountRequestDTO>> validate(String reason) {
    return validator.validate(new BlockAccountRequestDTO(reason));
  }

  private static Class<?> onlyViolation(Set<ConstraintViolation<BlockAccountRequestDTO>> violations) {
    assertEquals(1, violations.size());
    return violations.iterator().next().getConstraintDescriptor().getAnnotation().annotationType();
  }

  @Test
  @DisplayName("A reason of 500 characters is valid")
  void reasonAtTheLimitIsValid() {
    // Act + Assert
    assertTrue(validate("a".repeat(500)).isEmpty());
  }

  @Test
  @DisplayName("A reason of 501 characters is too long")
  void reasonOverTheLimitIsInvalid() {
    // Act + Assert
    assertEquals(Size.class, onlyViolation(validate("a".repeat(501))));
  }

  @Test
  @DisplayName("A one-character reason is valid")
  void shortReasonIsValid() {
    // Act + Assert
    assertTrue(validate("a").isEmpty());
  }

  @ParameterizedTest(name = "\"{0}\"")
  @ValueSource(strings = { "", "   " })
  @DisplayName("A blank reason is invalid")
  void blankReasonIsInvalid(String reason) {
    // Act + Assert
    assertEquals(NotBlank.class, onlyViolation(validate(reason)));
  }

  @Test
  @DisplayName("A missing reason is invalid")
  void missingReasonIsInvalid() {
    // Act + Assert
    assertEquals(NotBlank.class, onlyViolation(validate(null)));
  }

  @Test
  @DisplayName("Spaces around the reason are removed")
  void reasonIsTrimmed() {
    // Act + Assert
    assertEquals("test reason", new BlockAccountRequestDTO("  test reason ").reason());
  }

  @Test
  @DisplayName("A missing reason stays missing instead of crashing")
  void nullReasonStaysNull() {
    // Act + Assert
    assertNull(new BlockAccountRequestDTO(null).reason());
  }

}
