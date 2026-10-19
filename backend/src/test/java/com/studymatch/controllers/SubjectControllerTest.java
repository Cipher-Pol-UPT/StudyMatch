package com.studymatch.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import com.studymatch.dtos.DegreePlacementDTO;
import com.studymatch.dtos.DegreePlacementResponseDTO;
import com.studymatch.dtos.SubjectEditionRequestDTO;
import com.studymatch.dtos.SubjectEditionResponseDTO;
import com.studymatch.dtos.SubjectRequestDTO;
import com.studymatch.dtos.SubjectResponseDTO;
import com.studymatch.dtos.TeacherSummaryDTO;
import com.studymatch.exceptions.ErrorCode;
import com.studymatch.exceptions.StudyMatchException;
import com.studymatch.models.AcademicYear;
import com.studymatch.models.Category;
import com.studymatch.models.Degree;
import com.studymatch.models.Subject;
import com.studymatch.models.SubjectEdition;
import com.studymatch.models.Teacher;
import com.studymatch.services.SubjectService;

class SubjectControllerTest {

  private static final SubjectEditionRequestDTO EDITION = new SubjectEditionRequestDTO(2026, List.of(1L), List.of("T1"));
  private static final SubjectRequestDTO REGISTER = new SubjectRequestDTO("MAT1", "Mathematics I", 6, 1L,
      List.of(new DegreePlacementDTO(10L, 1, 1)), EDITION);

  private SubjectService subjectService;
  private SubjectController controller;

  @BeforeEach
  void setUp() {
    subjectService = mock(SubjectService.class);
    controller = new SubjectController(subjectService);
  }

  private static SubjectEdition edition(Subject subject, Teacher... teachers) {
    SubjectEdition edition = new SubjectEdition(subject, new AcademicYear(2026), Set.of(teachers));
    edition.addClassSection("T2");
    edition.addClassSection("T1");
    return edition;
  }

  private static Teacher teacher(String name) {
    return new Teacher(name + "@example.com", name + "@studymatch.com", name, "hash");
  }

  private static Subject subject(String code) {
    return new Subject(code, "Subject " + code, 6, new Category("Mathematics"));
  }

  // register subject

  @Test
  @DisplayName("Registering a subject is 201 with its edition: year, sorted teachers and classes, and the subject inside")
  void registerSubjectIs201() {
    // Arrange - @PreAuthorize isn't active without Spring, so the role check isn't tested here
    Subject mat1 = subject("MAT1");
    mat1.addDegree(new Degree("Computer Science", 3), 1, 2);
    when(subjectService.registerSubject(REGISTER)).thenReturn(edition(mat1, teacher("Rui"), teacher("Ana")));

    // Act
    ResponseEntity<SubjectEditionResponseDTO> result = controller.registerSubject(REGISTER);

    // Assert
    assertEquals(201, result.getStatusCode().value());
    SubjectEditionResponseDTO body = result.getBody();
    assertEquals(2026, body.academicYear());
    assertEquals(List.of("Ana", "Rui"), body.teachers().stream().map(TeacherSummaryDTO::name).toList());
    assertEquals(List.of("T1", "T2"), body.classSections());
    assertEquals("MAT1", body.subject().code());
    assertEquals("Mathematics", body.subject().category().name());
    assertEquals(List.of(new DegreePlacementResponseDTO(null, "Computer Science", 1, 2)), body.subject().degrees());
  }

  @Test
  @DisplayName("The degrees come out sorted by curricular year, then semester, then degree name")
  void degreesAreSorted() {
    // Arrange
    Subject mat1 = subject("MAT1");
    mat1.addDegree(new Degree("Physics", 5), 2, 1);
    mat1.addDegree(new Degree("Mathematics", 3), 1, 2);
    mat1.addDegree(new Degree("Computer Science", 3), 1, 2);
    when(subjectService.registerSubject(REGISTER)).thenReturn(edition(mat1, teacher("Ana")));

    // Act
    ResponseEntity<SubjectEditionResponseDTO> result = controller.registerSubject(REGISTER);

    // Assert
    assertEquals(List.of("Computer Science", "Mathematics", "Physics"),
        result.getBody().subject().degrees().stream().map(DegreePlacementResponseDTO::degreeName).toList());
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

  // new edition of an existing subject

  @Test
  @DisplayName("Adding an edition to an existing subject is 201 with the edition")
  void registerEditionIs201() {
    // Arrange
    when(subjectService.registerEdition(5L, EDITION)).thenReturn(edition(subject("MAT1"), teacher("Ana")));

    // Act
    ResponseEntity<SubjectEditionResponseDTO> result = controller.registerEdition(5L, EDITION);

    // Assert
    assertEquals(201, result.getStatusCode().value());
    assertEquals("MAT1", result.getBody().subject().code());
    assertEquals(2026, result.getBody().academicYear());
  }

  @Test
  @DisplayName("When the subject already has that year's edition, the service's error goes through to the handler")
  void duplicateEditionIsPassedOn() {
    // Arrange
    when(subjectService.registerEdition(5L, EDITION))
        .thenThrow(new StudyMatchException(ErrorCode.SUBJECT_EDITION_ALREADY_EXISTS));

    // Act
    StudyMatchException e = assertThrows(StudyMatchException.class, () -> controller.registerEdition(5L, EDITION));

    // Assert
    assertEquals(ErrorCode.SUBJECT_EDITION_ALREADY_EXISTS, e.getCode());
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
