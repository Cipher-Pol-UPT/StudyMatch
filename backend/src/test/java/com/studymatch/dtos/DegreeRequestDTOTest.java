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

class DegreeRequestDTOTest {

  private static Validator validator;

  @BeforeAll
  static void createValidator() {
    validator = Validation.buildDefaultValidatorFactory().getValidator();
  }

  // field -> annotation that failed, e.g. name=NotBlank
  private Set<String> failures(DegreeRequestDTO request) {
    return validator.validate(request).stream()
        .map(v -> v.getPropertyPath() + "=" + v.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName())
        .collect(Collectors.toSet());
  }

  @Test
  @DisplayName("A normal name is accepted")
  void validName() {
    assertTrue(failures(new DegreeRequestDTO("Mathematics", 3)).isEmpty());
  }

  @ParameterizedTest(name = "\"{0}\" is rejected")
  @ValueSource(strings = { "", "   " })
  @DisplayName("An empty or only-spaces name is NOT_BLANK")
  void blankName(String name) {
    assertEquals(Set.of("name=NotBlank"), failures(new DegreeRequestDTO(name, 3)));
  }

  @Test
  @DisplayName("A missing name is NOT_BLANK")
  void missingName() {
    assertEquals(Set.of("name=NotBlank"), failures(new DegreeRequestDTO(null, 3)));
  }

  @Test
  @DisplayName("A name over 100 characters is too long (boundary: 100 ok, 101 fails)")
  void nameLength() {
    assertTrue(failures(new DegreeRequestDTO("a".repeat(100), 3)).isEmpty());
    assertEquals(Set.of("name=Size"), failures(new DegreeRequestDTO("a".repeat(101), 3)));
  }

  @ParameterizedTest(name = "{0} curricular years is accepted")
  @ValueSource(ints = { 1, 6 })
  @DisplayName("Curricular years from 1 to 6 are accepted")
  void validCurricularYears(int years) {
    assertTrue(failures(new DegreeRequestDTO("Mathematics", years)).isEmpty());
  }

  @Test
  @DisplayName("Curricular years outside 1 to 6 are rejected (boundary: 0 fails Min, 7 fails Max)")
  void curricularYearsOutOfRange() {
    assertEquals(Set.of("curricularYears=Min"), failures(new DegreeRequestDTO("Mathematics", 0)));
    assertEquals(Set.of("curricularYears=Max"), failures(new DegreeRequestDTO("Mathematics", 7)));
  }

  @Test
  @DisplayName("Missing curricular years is NOT_NULL")
  void missingCurricularYears() {
    assertEquals(Set.of("curricularYears=NotNull"), failures(new DegreeRequestDTO("Mathematics", null)));
  }

}
