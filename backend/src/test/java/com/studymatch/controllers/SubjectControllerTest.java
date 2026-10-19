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

import com.studymatch.dtos.DegreePlacementDTO;
import com.studymatch.dtos.DegreePlacementResponseDTO;
import com.studymatch.dtos.SubjectRequestDTO;
import com.studymatch.dtos.SubjectResponseDTO;
import com.studymatch.exceptions.ErrorCode;
import com.studymatch.exceptions.StudyMatchException;
import com.studymatch.models.Category;
import com.studymatch.models.Degree;
import com.studymatch.models.Subject;
import com.studymatch.services.SubjectService;

class SubjectControllerTest {

  private static final SubjectRequestDTO REGISTER = new SubjectRequestDTO("MAT1", "Mathematics I", 6, 1L,
      List.of(new DegreePlacementDTO(10L, 1, 1)));

  private SubjectService subjectService;
  private SubjectController controller;

  @BeforeEach
  void setUp() {
    subjectService = mock(SubjectService.class);
    controller = new SubjectController(subjectService);
  }

  private static Subject subject(String code) {
    return new Subject(code, "Subject " + code, 6, new Category("Mathematics"));
  }

  // register subject

  @Test
  @DisplayName("Registering a subject is 201 with the new subject's data, category and degrees")
  void registerSubjectIs201() {
    // Arrange - @PreAuthorize isn't active without Spring, so the role check isn't tested here
    Subject mat1 = subject("MAT1");
    mat1.addDegree(new Degree("Computer Science", 3), 1, 2);
    when(subjectService.registerSubject(REGISTER)).thenReturn(mat1);

    // Act
    ResponseEntity<SubjectResponseDTO> result = controller.registerSubject(REGISTER);

    // Assert
    assertEquals(201, result.getStatusCode().value());
    assertEquals("MAT1", result.getBody().code());
    assertEquals("Mathematics", result.getBody().category().name());
    assertEquals(List.of(new DegreePlacementResponseDTO(null, "Computer Science", 1, 2)), result.getBody().degrees());
  }

  @Test
  @DisplayName("The degrees come out sorted by curricular year, then semester, then degree name")
  void degreesAreSorted() {
    // Arrange
    Subject mat1 = subject("MAT1");
    mat1.addDegree(new Degree("Physics", 5), 2, 1);
    mat1.addDegree(new Degree("Mathematics", 3), 1, 2);
    mat1.addDegree(new Degree("Computer Science", 3), 1, 2);
    when(subjectService.registerSubject(REGISTER)).thenReturn(mat1);

    // Act
    ResponseEntity<SubjectResponseDTO> result = controller.registerSubject(REGISTER);

    // Assert
    assertEquals(List.of("Computer Science", "Mathematics", "Physics"),
        result.getBody().degrees().stream().map(DegreePlacementResponseDTO::degreeName).toList());
  }

  @Test
  @DisplayName("When the subject can't be registered, the service's error goes through to the handler")
  void registerErrorIsPassedOn() {
    // Arrange
    when(subjectService.registerSubject(REGISTER))
        .thenThrow(new StudyMatchException(ErrorCode.SUBJECT_ALREADY_EXISTS));

    // Act
    StudyMatchException e = assertThrows(StudyMatchException.class, () -> controller.registerSubject(REGISTER));

    // Assert
    assertEquals(ErrorCode.SUBJECT_ALREADY_EXISTS, e.getCode());
  }

  // list subjects

  @Test
  @DisplayName("Listing subjects is 200 with each subject's data, in the service's order")
  void listSubjectsIs200() {
    // Arrange
    when(subjectService.listSubjects()).thenReturn(List.of(subject("ALG1"), subject("MAT1")));

    // Act
    ResponseEntity<List<SubjectResponseDTO>> result = controller.listSubjects();

    // Assert
    assertEquals(200, result.getStatusCode().value());
    assertEquals(List.of("ALG1", "MAT1"), result.getBody().stream().map(SubjectResponseDTO::code).toList());
  }

  @Test
  @DisplayName("With no subjects it's 200 with an empty list")
  void noSubjectsIsEmptyList() {
    // Arrange
    when(subjectService.listSubjects()).thenReturn(List.of());

    // Act
    ResponseEntity<List<SubjectResponseDTO>> result = controller.listSubjects();

    // Assert
    assertEquals(200, result.getStatusCode().value());
    assertEquals(List.of(), result.getBody());
  }

  // get one subject

  @Test
  @DisplayName("Getting a subject is 200 with its data")
  void getSubjectIs200() {
    // Arrange
    when(subjectService.getSubject(1L)).thenReturn(subject("MAT1"));

    // Act
    ResponseEntity<SubjectResponseDTO> result = controller.getSubject(1L);

    // Assert
    assertEquals(200, result.getStatusCode().value());
    assertEquals("MAT1", result.getBody().code());
  }

  @Test
  @DisplayName("When the subject doesn't exist, the service's error goes through to the handler")
  void unknownSubjectIsPassedOn() {
    // Arrange
    when(subjectService.getSubject(99L)).thenThrow(new StudyMatchException(ErrorCode.NOT_FOUND));

    // Act
    StudyMatchException e = assertThrows(StudyMatchException.class, () -> controller.getSubject(99L));

    // Assert
    assertEquals(ErrorCode.NOT_FOUND, e.getCode());
  }

}
