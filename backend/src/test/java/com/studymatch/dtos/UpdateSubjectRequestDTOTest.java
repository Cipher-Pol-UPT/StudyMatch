package com.studymatch.dtos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import jakarta.validation.Validation;
import jakarta.validation.Validator;

class UpdateSubjectRequestDTOTest {

  private static Validator validator;

  @BeforeAll
  static void createValidator() {
    validator = Validation.buildDefaultValidatorFactory().getValidator();
  }

  // field -> annotation that failed, e.g. name=NotBlank
  private Set<String> failures(UpdateSubjectRequestDTO request) {
    return validator.validate(request).stream()
        .map(v -> v.getPropertyPath() + "=" + v.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName())
        .collect(Collectors.toSet());
  }

  private UpdateSubjectRequestDTO withName(String name) {
    return new UpdateSubjectRequestDTO(name, 6, List.of(1L));
  }

  private UpdateSubjectRequestDTO withEcts(Integer ects) {
    return new UpdateSubjectRequestDTO("Mathematics I", ects, List.of(1L));
  }

  private UpdateSubjectRequestDTO withTeachers(List<Long> teacherNumbers) {
    return new UpdateSubjectRequestDTO("Mathematics I", 6, teacherNumbers);
  }

  @Test
  @DisplayName("A name, ECTS and one teacher are accepted")
  void validUpdate() {
    assertTrue(failures(withName("Mathematics I")).isEmpty());
  }

  // name

  @ParameterizedTest(name = "\"{0}\" is rejected")
  @ValueSource(strings = { "", "   " })
  @DisplayName("An empty or only-spaces name is NOT_BLANK")
  void blankName(String name) {
    assertEquals(Set.of("name=NotBlank"), failures(withName(name)));
  }

  @Test
  @DisplayName("A missing name is NOT_BLANK")
  void missingName() {
    assertEquals(Set.of("name=NotBlank"), failures(withName(null)));
  }

  @Test
  @DisplayName("A name over 100 characters is too long (boundary: 100 ok, 101 fails)")
  void nameLength() {
    assertTrue(failures(withName("a".repeat(100))).isEmpty());
    assertEquals(Set.of("name=Size"), failures(withName("a".repeat(101))));
  }

  // ects

  @Test
  @DisplayName("Missing ECTS is NOT_NULL")
  void missingEcts() {
    assertEquals(Set.of("ects=NotNull"), failures(withEcts(null)));
  }

  @Test
  @DisplayName("ECTS go from 1 to 60 (boundaries: 0 and 61 fail)")
  void ectsRange() {
    assertTrue(failures(withEcts(1)).isEmpty());
    assertTrue(failures(withEcts(60)).isEmpty());
    assertEquals(Set.of("ects=Min"), failures(withEcts(0)));
    assertEquals(Set.of("ects=Max"), failures(withEcts(61)));
  }

  // teachers

  @Test
  @DisplayName("The teachers are optional: a missing list leaves them as they are")
  void missingTeachers() {
    assertTrue(failures(withTeachers(null)).isEmpty());
  }

  @Test
  @DisplayName("An empty teacher list gets through: the edition refuses it with its own error")
  void emptyTeachers() {
    assertTrue(failures(withTeachers(List.of())).isEmpty());
  }

  @Test
  @DisplayName("A null entry in the teachers is NOT_NULL")
  void nullTeacher() {
    List<Long> teacherNumbers = new ArrayList<>();
    teacherNumbers.add(null);
    assertEquals(Set.of("teacherNumbers[0].<list element>=NotNull"), failures(withTeachers(teacherNumbers)));
  }

}
