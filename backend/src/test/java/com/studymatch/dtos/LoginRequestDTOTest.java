package com.studymatch.dtos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LoginRequestDTOTest {

  @Test
  @DisplayName("Spaces around the email are removed")
  void emailIsTrimmed() {
    // Act
    LoginRequestDTO request = new LoginRequestDTO("  ana.admin@test.com ", "Demo1234");

    // Assert
    assertEquals("ana.admin@test.com", request.email());
  }

  @Test
  @DisplayName("A missing email stays missing instead of crashing")
  void nullEmailStaysNull() {
    // Act - validation still has to be able to say NOT_BLANK
    LoginRequestDTO request = new LoginRequestDTO(null, "Demo1234");

    // Assert
    assertNull(request.email());
  }

  @Test
  @DisplayName("The password is never trimmed")
  void passwordIsNotTrimmed() {
    // Act - spaces can be part of a real password
    LoginRequestDTO request = new LoginRequestDTO("ana.admin@test.com", " pass word ");

    // Assert
    assertEquals(" pass word ", request.password());
  }

}
