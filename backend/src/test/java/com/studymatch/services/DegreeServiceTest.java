package com.studymatch.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.studymatch.exceptions.ErrorCode;
import com.studymatch.exceptions.StudyMatchException;
import com.studymatch.models.Degree;
import com.studymatch.repositories.DegreeRepository;

class DegreeServiceTest {

  private DegreeRepository degreeRepository;
  private DegreeService degreeService;

  @BeforeEach
  void setUp() {
    degreeRepository = mock(DegreeRepository.class);
    degreeService = new DegreeService(degreeRepository);
  }

  @Test
  @DisplayName("Degrees are looked up by name, ignoring spaces around it")
  void findByNameTrims() {
    // Arrange
    Degree degree = new Degree("Test Degree", 3);
    when(degreeRepository.findByNameIgnoreCase("Test Degree")).thenReturn(Optional.of(degree));

    // Act + Assert
    assertSame(degree, degreeService.getByName("  Test Degree "));
  }

  @Test
  @DisplayName("An unknown degree is DEGREE_NOT_FOUND")
  void unknownDegree() {
    StudyMatchException e = assertThrows(StudyMatchException.class, () -> degreeService.getByName("Nope"));
    assertEquals(ErrorCode.DEGREE_NOT_FOUND, e.getCode());
    assertTrue(degreeService.findByName("Nope").isEmpty());
  }

  @Test
  @DisplayName("The list comes from the repository already sorted by name")
  void listAll() {
    List<Degree> degrees = List.of(new Degree("A Degree", 3), new Degree("B Degree", 5));
    when(degreeRepository.findAllByOrderByNameAsc()).thenReturn(degrees);
    assertSame(degrees, degreeService.findAll());
  }

}
