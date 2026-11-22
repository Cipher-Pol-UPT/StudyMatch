package com.studymatch.dtos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

class AddMembersRequestDTOTest {

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

  private Set<ConstraintViolation<AddMembersRequestDTO>> validate(List<Long> members) {
    return validator.validate(new AddMembersRequestDTO(members));
  }

  private static Class<?> onlyViolation(Set<ConstraintViolation<AddMembersRequestDTO>> violations) {
    assertEquals(1, violations.size());
    return violations.iterator().next().getConstraintDescriptor().getAnnotation().annotationType();
  }

  @Test
  @DisplayName("One person is enough")
  void onePersonIsValid() {
    // Act + Assert
    assertTrue(validate(List.of(1004L)).isEmpty());
  }

  @Test
  @DisplayName("An empty list is refused")
  void emptyListRefused() {
    // Act + Assert
    assertEquals(NotEmpty.class, onlyViolation(validate(List.of())));
  }

  @Test
  @DisplayName("No list is refused")
  void nullListRefused() {
    // Act + Assert
    assertEquals(NotEmpty.class, onlyViolation(validate(null)));
  }

  @Test
  @DisplayName("A null number in the list is refused")
  void nullNumberRefused() {
    // Act + Assert
    assertEquals(NotNull.class, onlyViolation(validate(Arrays.asList(1004L, null))));
  }

}
