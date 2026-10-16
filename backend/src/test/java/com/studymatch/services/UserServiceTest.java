package com.studymatch.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.studymatch.exceptions.ErrorCode;
import com.studymatch.exceptions.StudyMatchException;
import com.studymatch.models.Teacher;
import com.studymatch.models.User;
import com.studymatch.repositories.UserRepository;

class UserServiceTest {

  private UserRepository userRepository;
  private UserService userService;
  private Teacher ana;

  @BeforeEach
  void setUp() {
    // fake repository (no db), real service
    userRepository = mock(UserRepository.class);
    userService = new UserService(userRepository);
    ana = new Teacher("ana@example.com", "ana@test.com", "Ana Test", "hash");
  }

  @Test
  @DisplayName("Email lookup ignores capitals and surrounding spaces")
  void findNormalisesEmail() {
    // Arrange - the fake only knows the lowercase email
    when(userRepository.findByInstitutionalEmail("ana@test.com")).thenReturn(Optional.of(ana));

    // Act
    Optional<User> result = userService.findByInstitutionalEmail("  ANA@Test.com ");

    // Assert
    assertSame(ana, result.orElseThrow());
    verify(userRepository).findByInstitutionalEmail("ana@test.com");
  }

  @Test
  @DisplayName("An unknown email finds nobody")
  void findUnknownEmailIsEmpty() {
    // nothing programmed -> the fake repository returns an empty Optional
    assertTrue(userService.findByInstitutionalEmail("nobody@test.com").isEmpty());
  }

  @Test
  @DisplayName("getByInstitutionalEmail returns the user when found")
  void getReturnsUser() {
    // Arrange
    when(userRepository.findByInstitutionalEmail("ana@test.com")).thenReturn(Optional.of(ana));

    // Act + Assert
    assertSame(ana, userService.getByInstitutionalEmail("ana@test.com"));
  }

  @Test
  @DisplayName("getByInstitutionalEmail throws NOT_FOUND when missing")
  void getMissingUserThrowsNotFound() {
    // Act
    StudyMatchException e = assertThrows(StudyMatchException.class,
        () -> userService.getByInstitutionalEmail("nobody@test.com"));

    // Assert
    assertEquals(ErrorCode.NOT_FOUND, e.getCode());
  }

}
