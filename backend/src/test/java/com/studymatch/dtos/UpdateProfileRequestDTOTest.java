package com.studymatch.dtos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.studymatch.enums.Language;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

class UpdateProfileRequestDTOTest {

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
  @DisplayName("A complete profile is valid")
  void completeProfileIsValid() {
    // Arrange
    UpdateProfileRequestDTO request = new UpdateProfileRequestDTO(null, "Test about me", Set.of("Test Skill"),
        Set.of("Test Interest"), Set.of(Language.PORTUGUESE));

    // Act
    Set<ConstraintViolation<UpdateProfileRequestDTO>> violations = validator.validate(request);

    // Assert
    assertTrue(violations.isEmpty());
  }

  @Test
  @DisplayName("About me can have 499 characters")
  void aboutMeUnderTheLimitIsValid() {
    // Act
    Set<ConstraintViolation<UpdateProfileRequestDTO>> violations = validator.validate(withAboutMe(499));

    // Assert
    assertTrue(violations.isEmpty());
  }

  @Test
  @DisplayName("About me can have exactly 500 characters")
  void aboutMeAtTheLimitIsValid() {
    // Act
    Set<ConstraintViolation<UpdateProfileRequestDTO>> violations = validator.validate(withAboutMe(500));

    // Assert
    assertTrue(violations.isEmpty());
  }

  @Test
  @DisplayName("About me cannot have 501 characters")
  void aboutMeOverTheLimitIsInvalid() {
    // Act + Assert
    assertOnlyViolation(withAboutMe(501), "aboutMe");
  }

  @Test
  @DisplayName("A skill can have exactly 50 characters")
  void skillAtTheLimitIsValid() {
    // Act
    Set<ConstraintViolation<UpdateProfileRequestDTO>> violations = validator.validate(withSkill("a".repeat(50)));

    // Assert
    assertTrue(violations.isEmpty());
  }

  @Test
  @DisplayName("A skill cannot have 51 characters")
  void skillOverTheLimitIsInvalid() {
    // Act + Assert
    assertOnlyViolation(withSkill("a".repeat(51)), "skills");
  }

  @Test
  @DisplayName("A blank skill is invalid")
  void blankSkillIsInvalid() {
    // Act + Assert
    assertOnlyViolation(withSkill(" "), "skills");
  }

  @Test
  @DisplayName("A blank interest is invalid")
  void blankInterestIsInvalid() {
    // Arrange
    UpdateProfileRequestDTO request = new UpdateProfileRequestDTO(null, null, Set.of(), Set.of(" "),
        Set.of(Language.PORTUGUESE));

    // Act + Assert
    assertOnlyViolation(request, "interests");
  }

  @Test
  @DisplayName("The languages are required")
  void missingLanguagesAreInvalid() {
    // Arrange
    UpdateProfileRequestDTO request = new UpdateProfileRequestDTO(null, null, Set.of(), Set.of(), null);

    // Act + Assert
    assertOnlyViolation(request, "languages");
  }

  private void assertOnlyViolation(UpdateProfileRequestDTO request, String field) {
    Set<ConstraintViolation<UpdateProfileRequestDTO>> violations = validator.validate(request);

    assertEquals(1, violations.size());
    assertTrue(violations.iterator().next().getPropertyPath().toString().startsWith(field));
  }

  private static UpdateProfileRequestDTO withAboutMe(int length) {
    return new UpdateProfileRequestDTO(null, "a".repeat(length), Set.of(), Set.of(), Set.of(Language.PORTUGUESE));
  }

  private static UpdateProfileRequestDTO withSkill(String skill) {
    return new UpdateProfileRequestDTO(null, null, Set.of(skill), Set.of(), Set.of(Language.PORTUGUESE));
  }

}
