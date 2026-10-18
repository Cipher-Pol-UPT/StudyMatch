package com.studymatch.dtos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ForgotPasswordRequestDTOTest {

  @Test
  @DisplayName("Spaces around the email are removed")
  void emailIsTrimmed() {
    assertEquals("ana@test.com", new ForgotPasswordRequestDTO("  ana@test.com ").email());
  }

  @Test
  @DisplayName("A missing email stays missing instead of crashing")
  void nullEmailStaysNull() {
    assertNull(new ForgotPasswordRequestDTO(null).email());
  }

}
