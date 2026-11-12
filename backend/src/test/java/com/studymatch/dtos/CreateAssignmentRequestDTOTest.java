package com.studymatch.dtos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.studymatch.enums.AssignmentType;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

class CreateAssignmentRequestDTOTest {

  private static final LocalDate DATE = LocalDate.of(2030, 6, 1);

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

  @Test
  @DisplayName("A group project with sizes and separations is valid")
  void groupProjectIsValid() {
    // Arrange
    CreateAssignmentRequestDTO request = withGrouping(new CreateAssignmentRequestDTO.Grouping(2, 3, 4, true,
        List.of(new CreateAssignmentRequestDTO.Separation(1L, 2L))));

    // Act
    Set<ConstraintViolation<CreateAssignmentRequestDTO>> violations = validator.validate(request);

    // Assert
    assertTrue(violations.isEmpty());
  }

  @Test
  @DisplayName("No grouping is valid, it's individual work")
  void individualWorkIsValid() {
    // Act
    Set<ConstraintViolation<CreateAssignmentRequestDTO>> violations = validator.validate(withWeight(30));

    // Assert
    assertTrue(violations.isEmpty());
  }

  // weight

  @ParameterizedTest
  @ValueSource(ints = { 1, 100 })
  @DisplayName("The weight can be exactly 1% or 100% (boundaries)")
  void weightAtTheLimitsIsValid(int weight) {
    // Act
    Set<ConstraintViolation<CreateAssignmentRequestDTO>> violations = validator.validate(withWeight(weight));

    // Assert
    assertTrue(violations.isEmpty());
  }

  @ParameterizedTest
  @ValueSource(ints = { 0, 101 })
  @DisplayName("The weight cannot be 0% or 101% (boundaries)")
  void weightOutsideTheLimitsIsInvalid(int weight) {
    // Act + Assert
    assertOnlyViolation(withWeight(weight), "weight");
  }

  @Test
  @DisplayName("The weight is required")
  void missingWeightIsInvalid() {
    // Act + Assert
    assertOnlyViolation(withWeight(null), "weight");
  }

  @Test
  @DisplayName("The weight is turned into the fraction the model stores: 30 is 0.30")
  void weightAsFraction() {
    // Act
    BigDecimal fraction = withWeight(30).weightFraction();

    // Assert
    assertEquals(new BigDecimal("0.30"), fraction);
  }

  // name, type and date

  @Test
  @DisplayName("The name can have 100 characters (boundary)")
  void nameAtTheLimitIsValid() {
    // Act
    Set<ConstraintViolation<CreateAssignmentRequestDTO>> violations = validator.validate(withName("a".repeat(100)));

    // Assert
    assertTrue(violations.isEmpty());
  }

  @Test
  @DisplayName("The name cannot have 101 characters (boundary)")
  void nameOverTheLimitIsInvalid() {
    // Act + Assert
    assertOnlyViolation(withName("a".repeat(101)), "name");
  }

  @Test
  @DisplayName("A blank name is invalid")
  void blankNameIsInvalid() {
    // Act + Assert
    assertOnlyViolation(withName(" "), "name");
  }

  @Test
  @DisplayName("The type and the date are required")
  void missingTypeAndDateAreInvalid() {
    // Arrange
    CreateAssignmentRequestDTO request = new CreateAssignmentRequestDTO("Project", null, null, 30, null);

    // Act
    Set<ConstraintViolation<CreateAssignmentRequestDTO>> violations = validator.validate(request);

    // Assert
    assertEquals(Set.of("type", "date"), fields(violations));
  }

  @Test
  @DisplayName("The date can be today (boundary)")
  void todayIsValid() {
    // Act
    Set<ConstraintViolation<CreateAssignmentRequestDTO>> violations = validator.validate(withDate(LocalDate.now()));

    // Assert
    assertTrue(violations.isEmpty());
  }

  @Test
  @DisplayName("The date cannot be yesterday (boundary)")
  void yesterdayIsInvalid() {
    // Act + Assert
    assertOnlyViolation(withDate(LocalDate.now().minusDays(1)), "date");
  }

  // grouping

  @Test
  @DisplayName("The grouping sizes are required")
  void missingGroupSizeIsInvalid() {
    // Act + Assert - errors inside the grouping are checked too
    assertOnlyViolation(withGrouping(new CreateAssignmentRequestDTO.Grouping(null, 3, 4, false, null)),
        "grouping.minSize");
  }

  @Test
  @DisplayName("Separations are optional")
  void groupingWithoutSeparationsIsValid() {
    // Arrange
    CreateAssignmentRequestDTO.Grouping grouping = new CreateAssignmentRequestDTO.Grouping(2, 3, 4, false, null);

    // Act
    Set<ConstraintViolation<CreateAssignmentRequestDTO>> violations = validator.validate(withGrouping(grouping));

    // Assert
    assertTrue(violations.isEmpty());
    assertEquals(List.of(), grouping.separationsOrEmpty());
  }

  @Test
  @DisplayName("A separation needs both students")
  void separationWithoutStudentIsInvalid() {
    // Arrange
    CreateAssignmentRequestDTO.Grouping grouping = new CreateAssignmentRequestDTO.Grouping(2, 3, 4, false,
        List.of(new CreateAssignmentRequestDTO.Separation(1L, null)));

    // Act + Assert
    assertOnlyViolation(withGrouping(grouping), "grouping.separations[0].secondStudent");
  }

  @Test
  @DisplayName("A separation can't be null")
  void nullSeparationIsInvalid() {
    // Arrange
    CreateAssignmentRequestDTO.Grouping grouping = new CreateAssignmentRequestDTO.Grouping(2, 3, 4, false,
        Arrays.asList((CreateAssignmentRequestDTO.Separation) null));

    // Act + Assert
    assertOnlyViolation(withGrouping(grouping), "grouping.separations[0].<list element>");
  }

  private void assertOnlyViolation(CreateAssignmentRequestDTO request, String field) {
    Set<ConstraintViolation<CreateAssignmentRequestDTO>> violations = validator.validate(request);

    assertEquals(Set.of(field), fields(violations));
  }

  private static Set<String> fields(Set<ConstraintViolation<CreateAssignmentRequestDTO>> violations) {
    return violations.stream().map(v -> v.getPropertyPath().toString()).collect(Collectors.toSet());
  }

  private static CreateAssignmentRequestDTO withWeight(Integer weight) {
    return new CreateAssignmentRequestDTO("Final Exam", AssignmentType.EXAM, DATE, weight, null);
  }

  private static CreateAssignmentRequestDTO withName(String name) {
    return new CreateAssignmentRequestDTO(name, AssignmentType.EXAM, DATE, 30, null);
  }

  private static CreateAssignmentRequestDTO withDate(LocalDate date) {
    return new CreateAssignmentRequestDTO("Final Exam", AssignmentType.EXAM, date, 30, null);
  }

  private static CreateAssignmentRequestDTO withGrouping(CreateAssignmentRequestDTO.Grouping grouping) {
    return new CreateAssignmentRequestDTO("Project", AssignmentType.PROJECT, DATE, 30, grouping);
  }

}
