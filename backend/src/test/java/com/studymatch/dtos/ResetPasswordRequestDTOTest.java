package com.studymatch.dtos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import jakarta.validation.Validation;
import jakarta.validation.Validator;

class ResetPasswordRequestDTOTest {

  private static Validator validator;

  @BeforeAll
  static void createValidator() {
    validator = Validation.buildDefaultValidatorFactory().getValidator();
  }

  // field -> annotation that failed, e.g. newPassword=Size
  private Set<String> failures(ResetPasswordRequestDTO request) {
    return validator.validate(request).stream()
        .map(v -> v.getPropertyPath() + "=" + v.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName())
        .collect(Collectors.toSet());
  }

  @Test
  @DisplayName("Spaces copied along with the email or the code are removed")
  void emailAndCodeAreTrimmed() {
    // Act
    ResetPasswordRequestDTO request = new ResetPasswordRequestDTO(" ana@test.com ", " 123456\n", "NewPass123");

    // Assert
    assertEquals("ana@test.com", request.email());
    assertEquals("123456", request.code());
  }

  @Test
  @DisplayName("The new password is never trimmed")
  void passwordIsNotTrimmed() {
    assertEquals(" NewPass123 ", new ResetPasswordRequestDTO("ana@test.com", "123456", " NewPass123 ").newPassword());
  }

  @Test
  @DisplayName("Missing email and code stay missing instead of crashing")
  void nullsStayNull() {
    // Act
    ResetPasswordRequestDTO request = new ResetPasswordRequestDTO(null, null, "NewPass123");

    // Assert - validation still has to be able to say NOT_BLANK
    assertNull(request.email());
    assertNull(request.code());
    assertEquals(Set.of("email=NotBlank", "code=NotBlank"), failures(request));
  }

  @Test
  @DisplayName("A valid request passes")
  void validRequest() {
    assertTrue(failures(new ResetPasswordRequestDTO("ana@test.com", "123456", "NewPass123")).isEmpty());
  }

  @Test
  @DisplayName("Uses the same password policy as change password")
  void samePasswordPolicy() {
    assertEquals(Set.of("newPassword=Size"), failures(new ResetPasswordRequestDTO("ana@test.com", "123456", "abc1")));
    assertEquals(Set.of("newPassword=Pattern"), failures(new ResetPasswordRequestDTO("ana@test.com", "123456", "abcdefgh")));
  }

}
