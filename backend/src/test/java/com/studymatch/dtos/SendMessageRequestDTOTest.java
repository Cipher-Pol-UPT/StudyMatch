package com.studymatch.dtos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.constraints.Pattern;
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
    return validator.validate(new SendMessageRequestDTO(text, null, null));
  }

  private Set<ConstraintViolation<SendMessageRequestDTO>> validateGif(String gifId) {
    return validator.validate(new SendMessageRequestDTO(null, gifId, null));
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
  @NullSource
  @ValueSource(strings = { "", "   ", "\n\t" })
  @DisplayName("Without text it needs a GIF (GR10)")
  void blankTextNeedsAGif(String text) {
    // Act + Assert
    assertFalse(new SendMessageRequestDTO(text, null, null).hasOneContent());
    assertTrue(new SendMessageRequestDTO(text, "abc123", null).hasOneContent());
  }

  @Test
  @DisplayName("With text it can't have a GIF (GR10)")
  void textAndGif() {
    // Act + Assert
    assertTrue(new SendMessageRequestDTO("hi", null, null).hasOneContent());
    assertFalse(new SendMessageRequestDTO("hi", "abc123", null).hasOneContent());
  }

  @Test
  @DisplayName("A GIF id of 1 to 64 characters is valid, 65 is too long")
  void gifIdLimits() {
    // Act + Assert
    assertTrue(validateGif("a").isEmpty());
    assertTrue(validateGif("a".repeat(64)).isEmpty());
    assertEquals(Pattern.class, onlyViolation(validateGif("a".repeat(65))));
  }

  @ParameterizedTest(name = "\"{0}\"")
  @ValueSource(strings = { "", " ", "abc 123", " abc123", "abc123\n", "abc-123", "abc,123", "../abc", "abc?x=1", "ábc" })
  @DisplayName("A GIF id with anything but letters and digits is invalid")
  void badGifId(String gifId) {
    // Act + Assert
    assertEquals(Pattern.class, onlyViolation(validateGif(gifId)));
  }

  @Test
  @DisplayName("Spaces and blank lines around the message are removed, the ones inside stay")
  void textIsStripped() {
    // Act + Assert
    assertEquals("hi\n  there", new SendMessageRequestDTO("\n hi\n  there \n", null, null).text());
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
  @DisplayName("A missing text is an empty one")
  void nullTextIsEmpty() {
    // Act + Assert
    assertEquals("", new SendMessageRequestDTO(null, "abc123", null).text());
  }

}
