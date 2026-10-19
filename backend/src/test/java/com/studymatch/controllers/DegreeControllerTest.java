package com.studymatch.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import com.studymatch.dtos.DegreeRequestDTO;
import com.studymatch.dtos.DegreeResponseDTO;
import com.studymatch.exceptions.ErrorCode;
import com.studymatch.exceptions.StudyMatchException;
import com.studymatch.models.Degree;
import com.studymatch.services.DegreeService;

class DegreeControllerTest {

  private static final DegreeRequestDTO REGISTER = new DegreeRequestDTO("Mathematics", 3);

  private DegreeService degreeService;
  private DegreeController controller;

  @BeforeEach
  void setUp() {
    degreeService = mock(DegreeService.class);
    controller = new DegreeController(degreeService);
  }

  // register degree

  @Test
  @DisplayName("Registering a degree is 201 with the new degree's data")
  void registerDegreeIs201() {
    // Arrange - @PreAuthorize isn't active without Spring, so the role check isn't tested here
    when(degreeService.registerDegree(REGISTER)).thenReturn(new Degree("Mathematics", 3));

    // Act
    ResponseEntity<DegreeResponseDTO> result = controller.registerDegree(REGISTER);

    // Assert
    assertEquals(201, result.getStatusCode().value());
    assertEquals("Mathematics", result.getBody().name());
    assertEquals(3, result.getBody().curricularYears());
  }

  @Test
  @DisplayName("When the degree already exists, the service's error goes through to the handler")
  void duplicateDegreeIsPassedOn() {
    // Arrange
    when(degreeService.registerDegree(REGISTER))
        .thenThrow(new StudyMatchException(ErrorCode.DEGREE_ALREADY_EXISTS));

    // Act
    StudyMatchException e = assertThrows(StudyMatchException.class, () -> controller.registerDegree(REGISTER));

    // Assert
    assertEquals(ErrorCode.DEGREE_ALREADY_EXISTS, e.getCode());
  }

  // list degrees

  @Test
  @DisplayName("Listing degrees is 200 with each degree's data, in the service's order")
  void listDegreesIs200() {
    // Arrange
    when(degreeService.listDegrees()).thenReturn(List.of(new Degree("Biology", 3), new Degree("Mathematics", 3)));

    // Act
    ResponseEntity<List<DegreeResponseDTO>> result = controller.listDegrees();

    // Assert
    assertEquals(200, result.getStatusCode().value());
    assertEquals(List.of("Biology", "Mathematics"), result.getBody().stream().map(DegreeResponseDTO::name).toList());
  }

  @Test
  @DisplayName("With no degrees it's 200 with an empty list")
  void noDegreesIsEmptyList() {
    // Arrange
    when(degreeService.listDegrees()).thenReturn(List.of());

    // Act
    ResponseEntity<List<DegreeResponseDTO>> result = controller.listDegrees();

    // Assert
    assertEquals(200, result.getStatusCode().value());
    assertEquals(List.of(), result.getBody());
  }

  // get one degree

  @Test
  @DisplayName("Getting a degree is 200 with its data")
  void getDegreeIs200() {
    // Arrange
    when(degreeService.getDegree(1L)).thenReturn(new Degree("Mathematics", 3));

    // Act
    ResponseEntity<DegreeResponseDTO> result = controller.getDegree(1L);

    // Assert
    assertEquals(200, result.getStatusCode().value());
    assertEquals("Mathematics", result.getBody().name());
  }

  @Test
  @DisplayName("When the degree doesn't exist, the service's error goes through to the handler")
  void unknownDegreeIsPassedOn() {
    // Arrange
    when(degreeService.getDegree(99L)).thenThrow(new StudyMatchException(ErrorCode.NOT_FOUND));

    // Act
    StudyMatchException e = assertThrows(StudyMatchException.class, () -> controller.getDegree(99L));

    // Assert
    assertEquals(ErrorCode.NOT_FOUND, e.getCode());
  }

}
