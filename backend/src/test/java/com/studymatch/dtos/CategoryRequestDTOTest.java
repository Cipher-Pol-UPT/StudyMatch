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

class CategoryRequestDTOTest {

  private static Validator validator;

  @BeforeAll
  static void createValidator() {
    validator = Validation.buildDefaultValidatorFactory().getValidator();
  }

  // field -> annotation that failed, e.g. name=NotBlank
  private Set<String> failures(CategoryRequestDTO request) {
    return validator.validate(request).stream()
        .map(v -> v.getPropertyPath() + "=" + v.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName())
        .collect(Collectors.toSet());
  }

  @Test
  @DisplayName("A normal name is accepted")
  void validName() {
    assertTrue(failures(new CategoryRequestDTO("Mathematics")).isEmpty());
  }

  @ParameterizedTest(name = "\"{0}\" is rejected")
  @ValueSource(strings = { "", "   " })
  @DisplayName("An empty or only-spaces name is NOT_BLANK")
  void blankName(String name) {
    assertEquals(Set.of("name=NotBlank"), failures(new CategoryRequestDTO(name)));
  }

  @Test
  @DisplayName("A missing name is NOT_BLANK")
  void missingName() {
    assertEquals(Set.of("name=NotBlank"), failures(new CategoryRequestDTO(null)));
  }

  @Test
  @DisplayName("A name over 100 characters is too long (boundary: 100 ok, 101 fails)")
  void nameLength() {
    assertTrue(failures(new CategoryRequestDTO("a".repeat(100))).isEmpty());
    assertEquals(Set.of("name=Size"), failures(new CategoryRequestDTO("a".repeat(101))));
  }

}
