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

class EditStudentRequestDTOTest {

  private static Validator validator;

  @BeforeAll
  static void createValidator() {
    validator = Validation.buildDefaultValidatorFactory().getValidator();
  }

  private Set<String> failures(EditStudentRequestDTO request) {
    return validator.validate(request).stream()
        .map(v -> v.getPropertyPath() + "=" + v.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName())
        .collect(Collectors.toSet());
  }

  private static EditStudentRequestDTO withName(String name) {
    return new EditStudentRequestDTO(name, "ana@example.com", 1L, 1);
  }

  private static EditStudentRequestDTO withEmail(String email) {
    return new EditStudentRequestDTO("Ana Test", email, 1L, 1);
  }

  private static EditStudentRequestDTO withYear(int year) {
    return new EditStudentRequestDTO("Ana Test", "ana@example.com", 1L, year);
  }

  // a valid address of exactly that many characters (64 before the @, domain labels of at most 63)
  private static String emailOfLength(int length) {
    String start = "a".repeat(64) + "@" + "b".repeat(63) + "." + "c".repeat(63) + ".";
    return start + "d".repeat(length - start.length() - ".com".length()) + ".com";
  }

  @Test
  @DisplayName("Name and email are trimmed and the email lowercased")
  void normalised() {
    EditStudentRequestDTO request = new EditStudentRequestDTO("  Ana Test ", " Ana@Example.com ", 1L, 2);
    assertEquals(new EditStudentRequestDTO("Ana Test", "ana@example.com", 1L, 2), request);
  }

  @Test
  @DisplayName("Everything missing gives one error per field")
  void everythingMissing() {
    assertEquals(Set.of("name=NotBlank", "personalEmail=NotBlank", "degreeId=NotNull", "curricularYear=NotNull"),
        failures(new EditStudentRequestDTO(null, null, null, null)));
  }

  @Test
  @DisplayName("Only spaces in the name or email count as blank")
  void onlySpacesIsBlank() {
    assertEquals(Set.of("name=NotBlank"), failures(withName("   ")));
    assertEquals(Set.of("personalEmail=NotBlank"), failures(withEmail("   ")));
  }

  @Test
  @DisplayName("A name of 100 characters is fine, 101 is too long (boundary)")
  void nameLength() {
    assertTrue(failures(withName("a".repeat(100))).isEmpty());
    assertEquals(Set.of("name=Size"), failures(withName("a".repeat(101))));
  }

  @Test
  @DisplayName("An email of 254 characters is fine, 255 is too long (boundary)")
  void emailLength() {
    assertTrue(failures(withEmail(emailOfLength(254))).isEmpty());
    assertTrue(failures(withEmail(emailOfLength(255))).contains("personalEmail=Size"));
  }

  @Test
  @DisplayName("An email without an @ is refused")
  void invalidEmail() {
    assertEquals(Set.of("personalEmail=Email"), failures(withEmail("ana.example.com")));
  }

  @Test
  @DisplayName("Years 1 and 6 pass the annotations (the degree's own length is checked by the service)")
  void yearBoundsInside() {
    assertTrue(failures(withYear(1)).isEmpty());
    assertTrue(failures(withYear(6)).isEmpty());
  }

  @Test
  @DisplayName("Year 0 and year 7 are out no matter the degree (boundaries)")
  void yearBoundsOutside() {
    assertEquals(Set.of("curricularYear=Min"), failures(withYear(0)));
    assertEquals(Set.of("curricularYear=Max"), failures(withYear(7)));
  }

}
