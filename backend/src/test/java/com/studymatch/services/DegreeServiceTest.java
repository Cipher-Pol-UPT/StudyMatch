package com.studymatch.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import com.studymatch.dtos.DegreeRequestDTO;
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

    // save returns what it was given
    when(degreeRepository.save(any(Degree.class))).thenAnswer(invocation -> invocation.getArgument(0));
  }

  // register degree

  @Test
  @DisplayName("A new degree is saved with its name and curricular years")
  void newDegreeIsSaved() {
    // Act
    Degree degree = degreeService.registerDegree(new DegreeRequestDTO("Mathematics", 3));

    // Assert
    assertEquals("Mathematics", degree.getName());
    assertEquals(3, degree.getCurricularYears());
    verify(degreeRepository).save(degree);
  }

  @Test
  @DisplayName("Spaces around the name are removed before checking and saving")
  void nameIsTrimmed() {
    // Act
    Degree degree = degreeService.registerDegree(new DegreeRequestDTO("  Mathematics  ", 3));

    // Assert
    assertEquals("Mathematics", degree.getName());
    verify(degreeRepository).existsByNameIgnoreCase("Mathematics");
  }

  @Test
  @DisplayName("A degree with a name that already exists gets DEGREE_ALREADY_EXISTS and isn't saved")
  void duplicateDegreeIsRefused() {
    // Arrange
    when(degreeRepository.existsByNameIgnoreCase("Mathematics")).thenReturn(true);

    // Act
    StudyMatchException e = assertThrows(StudyMatchException.class,
        () -> degreeService.registerDegree(new DegreeRequestDTO("Mathematics", 3)));

    // Assert
    assertEquals(ErrorCode.DEGREE_ALREADY_EXISTS, e.getCode());
    verify(degreeRepository, never()).save(any());
  }

  @Test
  @DisplayName("If someone else saves the same name first, the unique column's error becomes DEGREE_ALREADY_EXISTS")
  void simultaneousDuplicateIsRefused() {
    // Arrange - the check passed, but by the time we save another Staff already registered it
    when(degreeRepository.save(any(Degree.class))).thenThrow(new DataIntegrityViolationException("unique name"));

    // Act
    StudyMatchException e = assertThrows(StudyMatchException.class,
        () -> degreeService.registerDegree(new DegreeRequestDTO("Mathematics", 3)));

    // Assert
    assertEquals(ErrorCode.DEGREE_ALREADY_EXISTS, e.getCode());
  }

  // list degrees

  @Test
  @DisplayName("Listing returns the degrees in the order the repository sorted them (by name)")
  void listReturnsSortedDegrees() {
    // Arrange
    List<Degree> sorted = List.of(new Degree("Biology", 3), new Degree("Mathematics", 3));
    when(degreeRepository.findAllByOrderByNameAsc()).thenReturn(sorted);

    // Act
    List<Degree> result = degreeService.listDegrees();

    // Assert
    assertEquals(sorted, result);
  }

  // get one degree

  @Test
  @DisplayName("Getting a degree by id returns it")
  void getDegreeById() {
    // Arrange
    Degree mathematics = new Degree("Mathematics", 3);
    when(degreeRepository.findById(1L)).thenReturn(Optional.of(mathematics));

    // Act
    Degree result = degreeService.getDegree(1L);

    // Assert
    assertEquals(mathematics, result);
  }

  @Test
  @DisplayName("An id with no degree gets NOT_FOUND")
  void unknownIdIsNotFound() {
    // Arrange
    when(degreeRepository.findById(99L)).thenReturn(Optional.empty());

    // Act
    StudyMatchException e = assertThrows(StudyMatchException.class, () -> degreeService.getDegree(99L));

    // Assert
    assertEquals(ErrorCode.NOT_FOUND, e.getCode());
  }

}
