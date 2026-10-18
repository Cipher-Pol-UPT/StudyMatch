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

import com.studymatch.enums.Role;

import jakarta.validation.Validation;
import jakarta.validation.Validator;

class CreateAccountRequestDTOTest {

  private static Validator validator;

  @BeforeAll
  static void createValidator() {
    validator = Validation.buildDefaultValidatorFactory().getValidator();
  }

  private static CreateAccountRequestDTO withUsername(String username) {
    return new CreateAccountRequestDTO(Role.TEACHER, "Test Teacher", "teacher@example.com", username);
  }

  // field -> annotation that failed, e.g. username=Pattern
  private Set<String> failures(CreateAccountRequestDTO request) {
    return validator.validate(request).stream()
        .map(v -> v.getPropertyPath() + "=" + v.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName())
        .collect(Collectors.toSet());
  }

  // username

  @ParameterizedTest(name = "\"{0}\" is accepted")
  @ValueSource(strings = { "joao.silva", "js42", "a", "a.b.c", "42" })
  @DisplayName("Lowercase letters, digits and single dots between them are accepted")
  void validUsernames(String username) {
    assertTrue(failures(withUsername(username)).isEmpty());
  }

  @ParameterizedTest(name = "\"{0}\" is rejected")
  @ValueSource(strings = { ".joao", "joao.", "joao..silva", "joão", "joao_silva", "joao silva", "joao@silva" })
  @DisplayName("Dots at the edges, double dots, accents and other symbols are rejected")
  void invalidUsernames(String username) {
    assertEquals(Set.of("username=Pattern"), failures(withUsername(username)));
  }

  @Test
  @DisplayName("Capitals and spaces around are fixed instead of refused")
  void usernameIsNormalised() {
    // Act
    CreateAccountRequestDTO request = withUsername("  Joao.Silva ");

    // Assert
    assertEquals("joao.silva", request.username());
    assertTrue(failures(request).isEmpty());
  }

  @Test
  @DisplayName("64 characters is the longest username (boundary: 64 ok)")
  void username64Characters() {
    assertTrue(failures(withUsername("a".repeat(64))).isEmpty());
  }

  @Test
  @DisplayName("65 characters is too long (boundary: 65 fails)")
  void username65Characters() {
    assertEquals(Set.of("username=Size"), failures(withUsername("a".repeat(65))));
  }

  // the other fields

  @Test
  @DisplayName("The personal email is trimmed and lowercased")
  void personalEmailIsNormalised() {
    CreateAccountRequestDTO request = new CreateAccountRequestDTO(Role.TEACHER, "Test Teacher", " Teacher@Example.com ", "t");
    assertEquals("teacher@example.com", request.personalEmail());
  }

  @Test
  @DisplayName("Everything missing gives one error per field")
  void everythingMissing() {
    assertEquals(Set.of("type=NotNull", "name=NotBlank", "personalEmail=NotBlank", "username=NotBlank"),
        failures(new CreateAccountRequestDTO(null, null, null, null)));
  }

  @Test
  @DisplayName("A name over 100 characters is too long (boundary: 100 ok, 101 fails)")
  void nameLength() {
    assertTrue(failures(new CreateAccountRequestDTO(Role.TEACHER, "a".repeat(100), "t@example.com", "t")).isEmpty());
    assertEquals(Set.of("name=Size"),
        failures(new CreateAccountRequestDTO(Role.TEACHER, "a".repeat(101), "t@example.com", "t")));
  }

  @Test
  @DisplayName("A badly written personal email is EMAIL")
  void invalidPersonalEmail() {
    assertEquals(Set.of("personalEmail=Email"),
        failures(new CreateAccountRequestDTO(Role.TEACHER, "Test Teacher", "not-an-email", "t")));
  }

}
