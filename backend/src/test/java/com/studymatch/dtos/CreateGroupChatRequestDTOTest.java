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
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

class CreateGroupChatRequestDTOTest {

  private static final List<Long> MEMBERS = List.of(1002L, 1003L);

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

  private Set<ConstraintViolation<CreateGroupChatRequestDTO>> validate(String name, List<Long> members) {
    return validator.validate(new CreateGroupChatRequestDTO(name, null, members));
  }

  private static Class<?> onlyViolation(Set<ConstraintViolation<CreateGroupChatRequestDTO>> violations) {
    assertEquals(1, violations.size());
    return violations.iterator().next().getConstraintDescriptor().getAnnotation().annotationType();
  }

  @Test
  @DisplayName("A name of 50 characters is valid")
  void nameAtTheLimit() {
    // Act + Assert
    assertTrue(validate("a".repeat(50), MEMBERS).isEmpty());
  }

  @Test
  @DisplayName("A name of 51 characters is too long")
  void nameOverTheLimit() {
    // Act + Assert
    assertEquals(Size.class, onlyViolation(validate("a".repeat(51), MEMBERS)));
  }

  @Test
  @DisplayName("Only spaces is stripped to nothing and refused")
  void blankNameRefused() {
    // Act + Assert
    assertEquals(NotBlank.class, onlyViolation(validate("   ", MEMBERS)));
  }

  @Test
  @DisplayName("Spaces around a 50 character name are stripped, so it still fits")
  void spacesAroundAreStripped() {
    // Arrange
    CreateGroupChatRequestDTO request = new CreateGroupChatRequestDTO("  " + "a".repeat(50) + "  ", null, MEMBERS);

    // Act + Assert
    assertEquals("a".repeat(50), request.name());
    assertTrue(validator.validate(request).isEmpty());
  }

  @Test
  @DisplayName("No name is refused")
  void nullNameRefused() {
    // Act + Assert
    assertEquals(NotBlank.class, onlyViolation(validate(null, MEMBERS)));
  }

  @Test
  @DisplayName("No member list is refused")
  void nullMembersRefused() {
    // Act + Assert
    assertEquals(NotNull.class, onlyViolation(validate("Study group", null)));
  }

  @Test
  @DisplayName("A null number in the list is refused")
  void nullNumberRefused() {
    // Act + Assert
    assertEquals(NotNull.class, onlyViolation(validate("Study group", Arrays.asList(1002L, null))));
  }

}
