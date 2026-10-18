package com.studymatch.dtos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import jakarta.validation.Validation;
import jakarta.validation.Validator;

class ChangePasswordRequestDTOTest {

  private static Validator validator;

  @BeforeAll
  static void createValidator() {
    // the same validator Spring runs on @Valid
    validator = Validation.buildDefaultValidatorFactory().getValidator();
  }

  // which annotations failed for this new password, e.g. [Size, Pattern]
  private Set<String> failedRules(String newPassword) {
    return validator.validate(new ChangePasswordRequestDTO("Current123", newPassword)).stream()
        .map(violation -> violation.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName())
        .collect(Collectors.toSet());
  }

  // length (boundaries 7 / 8 and 72 / 73)

  @Test
  @DisplayName("7 characters is too short")
  void sevenCharactersFails() {
    assertEquals(Set.of("Size"), failedRules("abcdef1"));
  }

  @Test
  @DisplayName("8 characters is the minimum")
  void eightCharactersPasses() {
    assertTrue(failedRules("abcdefg1").isEmpty());
  }

  @Test
  @DisplayName("72 characters is the maximum")
  void seventyTwoCharactersPasses() {
    assertTrue(failedRules("a".repeat(71) + "1").isEmpty());
  }

  @Test
  @DisplayName("73 characters is too long")
  void seventyThreeCharactersFails() {
    assertEquals(Set.of("Size"), failedRules("a".repeat(72) + "1"));
  }

  // letters and numbers

  @ParameterizedTest(name = "\"{0}\" is accepted")
  @ValueSource(strings = { "abc12345", "12345abc", "a1b2c3d4", "çãéõúíó1", "Pass 1234" })
  @DisplayName("Letters and digits in any order are accepted, accents count as letters")
  void lettersAndDigitsPass(String newPassword) {
    assertTrue(failedRules(newPassword).isEmpty());
  }

  @ParameterizedTest(name = "\"{0}\" is rejected")
  @ValueSource(strings = { "abcdefgh", "12345678", "!!!!!!!!", "abc!@#$%" })
  @DisplayName("Only letters, only digits or only symbols are rejected")
  void missingLetterOrDigitFails(String newPassword) {
    assertEquals(Set.of("Pattern"), failedRules(newPassword));
  }

  // missing values

  @Test
  @DisplayName("A missing new password is NOT_BLANK")
  void missingNewPassword() {
    // null passes @Size and @Pattern on purpose (they skip null), so only NotBlank fails
    assertEquals(Set.of("NotBlank"), failedRules(null));
  }

  @Test
  @DisplayName("The current password is optional here, the service decides")
  void currentPasswordIsOptional() {
    assertTrue(validator.validate(new ChangePasswordRequestDTO(null, "NewPass123")).isEmpty());
  }

}
