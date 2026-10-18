package com.studymatch.dtos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import jakarta.validation.Validation;
import jakarta.validation.Validator;

class RegisterStudentRequestDTOTest {

  private static Validator validator;

  @BeforeAll
  static void createValidator() {
    validator = Validation.buildDefaultValidatorFactory().getValidator();
  }

  private Set<String> failures(RegisterStudentRequestDTO request) {
    return validator.validate(request).stream()
        .map(v -> v.getPropertyPath() + "=" + v.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName())
        .collect(Collectors.toSet());
  }

  @Test
  @DisplayName("Name, email and degree are trimmed and the email lowercased")
  void normalised() {
    RegisterStudentRequestDTO request = new RegisterStudentRequestDTO("  Ana Test ", " Ana@Example.com ", " Test Degree ", 1);
    assertEquals(new RegisterStudentRequestDTO("Ana Test", "ana@example.com", "Test Degree", 1), request);
  }

  @Test
  @DisplayName("Everything missing gives one error per field")
  void everythingMissing() {
    assertEquals(Set.of("name=NotBlank", "personalEmail=NotBlank", "degreeProgramme=NotBlank", "curricularYear=NotNull"),
        failures(new RegisterStudentRequestDTO(null, null, null, null)));
  }

  @Test
  @DisplayName("Years 1 and 6 pass the annotations (the degree's own length is checked later)")
  void yearBoundsInside() {
    assertTrue(failures(new RegisterStudentRequestDTO("Ana", "a@example.com", "D", 1)).isEmpty());
    assertTrue(failures(new RegisterStudentRequestDTO("Ana", "a@example.com", "D", 6)).isEmpty());
  }

  @Test
  @DisplayName("Year 0 and year 7 are out no matter the degree (boundaries)")
  void yearBoundsOutside() {
    assertEquals(Set.of("curricularYear=Min"), failures(new RegisterStudentRequestDTO("Ana", "a@example.com", "D", 0)));
    assertEquals(Set.of("curricularYear=Max"), failures(new RegisterStudentRequestDTO("Ana", "a@example.com", "D", 7)));
  }

}
