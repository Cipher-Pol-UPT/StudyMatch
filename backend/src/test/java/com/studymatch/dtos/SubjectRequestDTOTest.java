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

class SubjectRequestDTOTest {

  private static final List<DegreePlacementDTO> ONE_DEGREE = List.of(new DegreePlacementDTO(1L, 1, 1));
  private static final SubjectEditionRequestDTO EDITION = new SubjectEditionRequestDTO(2026, List.of(1L), List.of("T1"));

  private static Validator validator;

  @BeforeAll
  static void createValidator() {
    validator = Validation.buildDefaultValidatorFactory().getValidator();
  }

  // field -> annotation that failed, e.g. name=NotBlank
  private Set<String> failures(SubjectRequestDTO request) {
    return validator.validate(request).stream()
        .map(v -> v.getPropertyPath() + "=" + v.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName())
        .collect(Collectors.toSet());
  }

  private SubjectRequestDTO withCode(String code) {
    return new SubjectRequestDTO(code, "Mathematics I", 6, 1L, ONE_DEGREE, EDITION);
  }

  private SubjectRequestDTO withName(String name) {
    return new SubjectRequestDTO("MAT1", name, 6, 1L, ONE_DEGREE, EDITION);
  }

  private SubjectRequestDTO withEcts(Integer ects) {
    return new SubjectRequestDTO("MAT1", "Mathematics I", ects, 1L, ONE_DEGREE, EDITION);
  }

  private SubjectRequestDTO withDegree(DegreePlacementDTO placement) {
    return new SubjectRequestDTO("MAT1", "Mathematics I", 6, 1L, List.of(placement), EDITION);
  }

  @Test
  @DisplayName("A normal subject in one degree is accepted")
  void validSubject() {
    assertTrue(failures(withCode("MAT1")).isEmpty());
  }

  // code

  @ParameterizedTest(name = "\"{0}\" is rejected")
  @ValueSource(strings = { "", "   " })
  @DisplayName("An empty or only-spaces code is NOT_BLANK")
  void blankCode(String code) {
    assertEquals(Set.of("code=NotBlank"), failures(withCode(code)));
  }

  @Test
  @DisplayName("A missing code is NOT_BLANK")
  void missingCode() {
    assertEquals(Set.of("code=NotBlank"), failures(withCode(null)));
  }

  @Test
  @DisplayName("A code over 20 characters is too long (boundary: 20 ok, 21 fails)")
  void codeLength() {
    assertTrue(failures(withCode("a".repeat(20))).isEmpty());
    assertEquals(Set.of("code=Size"), failures(withCode("a".repeat(21))));
  }

  // name

  @ParameterizedTest(name = "\"{0}\" is rejected")
  @ValueSource(strings = { "", "   " })
  @DisplayName("An empty or only-spaces name is NOT_BLANK")
  void blankName(String name) {
    assertEquals(Set.of("name=NotBlank"), failures(withName(name)));
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

  // category

  @Test
  @DisplayName("A missing category is NOT_NULL")
  void missingCategory() {
    assertEquals(Set.of("categoryId=NotNull"),
        failures(new SubjectRequestDTO("MAT1", "Mathematics I", 6, null, ONE_DEGREE, EDITION)));
  }

  // degrees

  @Test
  @DisplayName("A subject with no degrees is NOT_EMPTY")
  void noDegrees() {
    assertEquals(Set.of("degrees=NotEmpty"), failures(new SubjectRequestDTO("MAT1", "Mathematics I", 6, 1L, List.of(), EDITION)));
    assertEquals(Set.of("degrees=NotEmpty"), failures(new SubjectRequestDTO("MAT1", "Mathematics I", 6, 1L, null, EDITION)));
  }

  @Test
  @DisplayName("A null entry in the degrees is NOT_NULL")
  void nullPlacement() {
    List<DegreePlacementDTO> degrees = new ArrayList<>();
    degrees.add(null);
    assertEquals(Set.of("degrees[0].<list element>=NotNull"),
        failures(new SubjectRequestDTO("MAT1", "Mathematics I", 6, 1L, degrees, EDITION)));
  }

  @Test
  @DisplayName("Each degree is checked: the degree is required")
  void placementMissingDegree() {
    assertEquals(Set.of("degrees[0].degreeId=NotNull"), failures(withDegree(new DegreePlacementDTO(null, 1, 1))));
  }

  @Test
  @DisplayName("Each degree is checked: the curricular year goes from 1 to 6")
  void placementCurricularYear() {
    assertTrue(failures(withDegree(new DegreePlacementDTO(1L, 6, 1))).isEmpty());
    assertEquals(Set.of("degrees[0].curricularYear=Min"), failures(withDegree(new DegreePlacementDTO(1L, 0, 1))));
    assertEquals(Set.of("degrees[0].curricularYear=Max"), failures(withDegree(new DegreePlacementDTO(1L, 7, 1))));
  }

  @Test
  @DisplayName("Each degree is checked: the semester is 1 or 2")
  void placementSemester() {
    assertTrue(failures(withDegree(new DegreePlacementDTO(1L, 1, 2))).isEmpty());
    assertEquals(Set.of("degrees[0].semester=Min"), failures(withDegree(new DegreePlacementDTO(1L, 1, 0))));
    assertEquals(Set.of("degrees[0].semester=Max"), failures(withDegree(new DegreePlacementDTO(1L, 1, 3))));
  }

  // edition

  private SubjectRequestDTO withEdition(SubjectEditionRequestDTO edition) {
    return new SubjectRequestDTO("MAT1", "Mathematics I", 6, 1L, ONE_DEGREE, edition);
  }

  @Test
  @DisplayName("A subject without its edition is NOT_NULL")
  void missingEdition() {
    assertEquals(Set.of("edition=NotNull"), failures(withEdition(null)));
  }

  @Test
  @DisplayName("The edition needs an academic year")
  void editionMissingAcademicYear() {
    assertEquals(Set.of("edition.academicYear=NotNull"),
        failures(withEdition(new SubjectEditionRequestDTO(null, List.of(1L), List.of()))));
  }

  @Test
  @DisplayName("The teacher list is required, but an empty one gets through: the edition refuses it with its own error")
  void editionTeachers() {
    assertEquals(Set.of("edition.teacherNumbers=NotNull"),
        failures(withEdition(new SubjectEditionRequestDTO(2026, null, List.of()))));
    assertTrue(failures(withEdition(new SubjectEditionRequestDTO(2026, List.of(), List.of()))).isEmpty());
  }

  @Test
  @DisplayName("Classes are optional: none or a missing list are both accepted")
  void editionWithoutClasses() {
    assertTrue(failures(withEdition(new SubjectEditionRequestDTO(2026, List.of(1L), List.of()))).isEmpty());
    assertTrue(failures(withEdition(new SubjectEditionRequestDTO(2026, List.of(1L), null))).isEmpty());
  }

  @Test
  @DisplayName("Each class code is checked: not blank, at most 20 characters")
  void editionClassCodes() {
    assertEquals(Set.of("edition.classSections[0].<list element>=NotBlank"),
        failures(withEdition(new SubjectEditionRequestDTO(2026, List.of(1L), List.of(" ")))));
    assertTrue(failures(withEdition(new SubjectEditionRequestDTO(2026, List.of(1L), List.of("a".repeat(20))))).isEmpty());
    assertEquals(Set.of("edition.classSections[0].<list element>=Size"),
        failures(withEdition(new SubjectEditionRequestDTO(2026, List.of(1L), List.of("a".repeat(21))))));
  }

}
