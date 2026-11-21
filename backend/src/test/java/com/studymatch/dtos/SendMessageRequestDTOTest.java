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

class SendMessageRequestDTOTest {

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

  private Set<ConstraintViolation<SendMessageRequestDTO>> validate(String text) {
    return validator.validate(new SendMessageRequestDTO(text));
  }

  private static Class<?> onlyViolation(Set<ConstraintViolation<SendMessageRequestDTO>> violations) {
    assertEquals(1, violations.size());
    return violations.iterator().next().getConstraintDescriptor().getAnnotation().annotationType();
  }

  @Test
  @DisplayName("A message of 2000 characters is valid (GR10)")
  void textAtTheLimitIsValid() {
    // Act + Assert
    assertTrue(validate("a".repeat(2000)).isEmpty());
  }

  @Test
  @DisplayName("A message of 2001 characters is too long (GR10)")
  void textOverTheLimitIsInvalid() {
    // Act + Assert
    assertEquals(Size.class, onlyViolation(validate("a".repeat(2001))));
  }

  @Test
  @DisplayName("A one-character message is valid")
  void shortTextIsValid() {
    // Act + Assert
    assertTrue(validate("a").isEmpty());
  }

  @ParameterizedTest(name = "\"{0}\"")
  @ValueSource(strings = { "", "   ", "\n\t" })
  @DisplayName("An empty message is invalid (GR10)")
  void blankTextIsInvalid(String text) {
    // Act + Assert
    assertEquals(NotBlank.class, onlyViolation(validate(text)));
  }

  @Test
  @DisplayName("A missing message is invalid")
  void missingTextIsInvalid() {
    // Act + Assert
    assertEquals(NotBlank.class, onlyViolation(validate(null)));
  }

  @Test
  @DisplayName("Spaces and blank lines around the message are removed, the ones inside stay")
  void textIsStripped() {
    // Act + Assert
    assertEquals("hi\n  there", new SendMessageRequestDTO("\n hi\n  there \n").text());
  }

  @Test
  @DisplayName("Spaces around don't count towards the 2000")
  void spacesAroundDontCount() {
    // Act + Assert
    assertTrue(validate("  " + "a".repeat(2000) + "  ").isEmpty());
  }

  @Test
  @DisplayName("An emoji counts as 2, like the text box's maxlength: 1000 fit, 1001 don't")
  void emojiCountAsTwo() {
    // Arrange
    String emoji = Character.toString(0x1F600);

    // Act + Assert
    assertTrue(validate(emoji.repeat(1000)).isEmpty());
    assertEquals(Size.class, onlyViolation(validate(emoji.repeat(1001))));
  }

  @Test
  @DisplayName("A missing message stays missing instead of crashing")
  void nullTextStaysNull() {
    // Act + Assert
    assertNull(new SendMessageRequestDTO(null).text());
  }

}
