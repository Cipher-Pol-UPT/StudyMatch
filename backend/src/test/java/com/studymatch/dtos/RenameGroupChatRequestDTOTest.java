package com.studymatch.dtos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// the same name rules as creating (#234)
class RenameGroupChatRequestDTOTest {

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

  private Set<ConstraintViolation<RenameGroupChatRequestDTO>> validate(String name) {
    return validator.validate(new RenameGroupChatRequestDTO(name));
  }

  private static Class<?> onlyViolation(Set<ConstraintViolation<RenameGroupChatRequestDTO>> violations) {
    assertEquals(1, violations.size());
    return violations.iterator().next().getConstraintDescriptor().getAnnotation().annotationType();
  }

  @Test
  @DisplayName("A name of 50 characters is valid")
  void nameAtTheLimit() {
    // Act + Assert
    assertTrue(validate("a".repeat(50)).isEmpty());
  }

  @Test
  @DisplayName("A name of 51 characters is too long")
  void nameOverTheLimit() {
    // Act + Assert
    assertEquals(Size.class, onlyViolation(validate("a".repeat(51))));
  }

  @Test
  @DisplayName("Only spaces is stripped to nothing and refused")
  void blankNameRefused() {
    // Act + Assert
    assertEquals(NotBlank.class, onlyViolation(validate("   ")));
  }

  @Test
  @DisplayName("Spaces around a 50 character name are stripped, so it still fits")
  void spacesAroundAreStripped() {
    // Arrange
    RenameGroupChatRequestDTO request = new RenameGroupChatRequestDTO("  " + "a".repeat(50) + "  ");

    // Act + Assert
    assertEquals("a".repeat(50), request.name());
    assertTrue(validator.validate(request).isEmpty());
  }

  @Test
  @DisplayName("No name is refused")
  void nullNameRefused() {
    // Act + Assert
    assertEquals(NotBlank.class, onlyViolation(validate(null)));
  }

}
