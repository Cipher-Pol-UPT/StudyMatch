package com.studymatch.dtos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.Set;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

class CreateStudyRoomRequestDTOTest {

  private static final LocalDateTime STARTS_AT = LocalDateTime.of(2030, 1, 1, 14, 0);
  private static final LocalDateTime ENDS_AT = LocalDateTime.of(2030, 1, 1, 16, 0);
  private static final String LINK = "https://meet.google.com/abc-defg-hij";

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
  @DisplayName("A complete request is valid")
  void completeRequestIsValid() {
    // Act
    Set<ConstraintViolation<CreateStudyRoomRequestDTO>> violations = validator.validate(withCapacity(5));

    // Assert
    assertTrue(violations.isEmpty());
  }

  @Test
  @DisplayName("The description is optional")
  void missingDescriptionIsValid() {
    // Arrange
    CreateStudyRoomRequestDTO request = new CreateStudyRoomRequestDTO(1L, STARTS_AT, ENDS_AT, null, 5, LINK);

    // Act
    Set<ConstraintViolation<CreateStudyRoomRequestDTO>> violations = validator.validate(request);

    // Assert
    assertTrue(violations.isEmpty());
  }

  // capacity

  @Test
  @DisplayName("The capacity cannot be 1")
  void capacityUnderTheMinimumIsInvalid() {
    // Act + Assert
    assertOnlyViolation(withCapacity(1), "capacity");
  }

  @Test
  @DisplayName("The capacity can be exactly 2")
  void capacityAtTheMinimumIsValid() {
    // Act
    Set<ConstraintViolation<CreateStudyRoomRequestDTO>> violations = validator.validate(withCapacity(2));

    // Assert
    assertTrue(violations.isEmpty());
  }

  @Test
  @DisplayName("The capacity can be exactly 20")
  void capacityAtTheMaximumIsValid() {
    // Act
    Set<ConstraintViolation<CreateStudyRoomRequestDTO>> violations = validator.validate(withCapacity(20));

    // Assert
    assertTrue(violations.isEmpty());
  }

  @Test
  @DisplayName("The capacity cannot be 21")
  void capacityOverTheMaximumIsInvalid() {
    // Act + Assert
    assertOnlyViolation(withCapacity(21), "capacity");
  }

  // description

  @Test
  @DisplayName("The description can have exactly 500 characters")
  void descriptionAtTheLimitIsValid() {
    // Act
    Set<ConstraintViolation<CreateStudyRoomRequestDTO>> violations = validator.validate(withDescription(500));

    // Assert
    assertTrue(violations.isEmpty());
  }

  @Test
  @DisplayName("The description cannot have 501 characters")
  void descriptionOverTheLimitIsInvalid() {
    // Act + Assert
    assertOnlyViolation(withDescription(501), "description");
  }

  // link

  @Test
  @DisplayName("A Google Meet link with query parameters is valid")
  void meetLinkWithQueryIsValid() {
    // Act
    Set<ConstraintViolation<CreateStudyRoomRequestDTO>> violations = validator
        .validate(withLink(LINK + "?authuser=0"));

    // Assert
    assertTrue(violations.isEmpty());
  }

  @ParameterizedTest
  @ValueSource(strings = {
      "https://zoom.us/j/123",
      "http://meet.google.com/abc-defg-hij",
      "https://meet.google.com/abcdefghij",
      "https://meet.google.com/ABC-DEFG-HIJ",
      "https://meet.google.com/abc-defg-hij/extra",
      "https://meet.google.com.evil.com/abc-defg-hij",
      "meet.google.com/abc-defg-hij" })
  @DisplayName("Anything that isn't a Google Meet link is invalid")
  void notAMeetLinkIsInvalid(String link) {
    // Act + Assert
    assertOnlyViolation(withLink(link), "link");
  }

  // required fields

  @Test
  @DisplayName("The subject is required")
  void missingSubjectIsInvalid() {
    // Act + Assert
    assertOnlyViolation(new CreateStudyRoomRequestDTO(null, STARTS_AT, ENDS_AT, "test room", 5, LINK), "subjectId");
  }

  @Test
  @DisplayName("The start is required")
  void missingStartIsInvalid() {
    // Act + Assert
    assertOnlyViolation(new CreateStudyRoomRequestDTO(1L, null, ENDS_AT, "test room", 5, LINK), "startsAt");
  }

  @Test
  @DisplayName("The end is required")
  void missingEndIsInvalid() {
    // Act + Assert
    assertOnlyViolation(new CreateStudyRoomRequestDTO(1L, STARTS_AT, null, "test room", 5, LINK), "endsAt");
  }

  @Test
  @DisplayName("The capacity is required")
  void missingCapacityIsInvalid() {
    // Act + Assert
    assertOnlyViolation(withCapacity(null), "capacity");
  }

  @Test
  @DisplayName("The link is required")
  void missingLinkIsInvalid() {
    // Act + Assert
    assertOnlyViolation(withLink(null), "link");
  }

  @Test
  @DisplayName("A blank link is invalid")
  void blankLinkIsInvalid() {
    // Act - blank also fails the Meet pattern
    Set<ConstraintViolation<CreateStudyRoomRequestDTO>> violations = validator.validate(withLink(" "));

    // Assert
    assertEquals(2, violations.size());
    assertTrue(violations.stream().allMatch(v -> v.getPropertyPath().toString().equals("link")));
  }

  private void assertOnlyViolation(CreateStudyRoomRequestDTO request, String field) {
    Set<ConstraintViolation<CreateStudyRoomRequestDTO>> violations = validator.validate(request);

    assertEquals(1, violations.size());
    assertEquals(field, violations.iterator().next().getPropertyPath().toString());
  }

  private static CreateStudyRoomRequestDTO withCapacity(Integer capacity) {
    return new CreateStudyRoomRequestDTO(1L, STARTS_AT, ENDS_AT, "test room", capacity, LINK);
  }

  private static CreateStudyRoomRequestDTO withDescription(int length) {
    return new CreateStudyRoomRequestDTO(1L, STARTS_AT, ENDS_AT, "a".repeat(length), 5, LINK);
  }

  private static CreateStudyRoomRequestDTO withLink(String link) {
    return new CreateStudyRoomRequestDTO(1L, STARTS_AT, ENDS_AT, "test room", 5, link);
  }

}
