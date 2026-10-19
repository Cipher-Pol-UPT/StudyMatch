package com.studymatch.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import com.studymatch.dtos.DegreePlacementDTO;
import com.studymatch.dtos.SubjectEditionRequestDTO;
import com.studymatch.dtos.SubjectRequestDTO;
import com.studymatch.exceptions.ErrorCode;
import com.studymatch.exceptions.StudyMatchException;
import com.studymatch.models.AcademicYear;
import com.studymatch.models.Category;
import com.studymatch.models.Degree;
import com.studymatch.models.DegreeSubject;
import com.studymatch.models.ModelTestData;
import com.studymatch.models.Subject;
import com.studymatch.models.SubjectEdition;
import com.studymatch.repositories.SubjectRepository;

class SubjectServiceTest {

  private static final SubjectEditionRequestDTO EDITION = new SubjectEditionRequestDTO(2026, List.of(1L), List.of("T1"));

  private SubjectRepository subjectRepository;
  private CategoryService categoryService;
  private DegreeService degreeService;
  private SubjectEditionService subjectEditionService;
  private SubjectService subjectService;

  private Category mathematics;
  private Degree computerScience;
  private Degree physics;

  @BeforeEach
  void setUp() {
    subjectRepository = mock(SubjectRepository.class);
    categoryService = mock(CategoryService.class);
    degreeService = mock(DegreeService.class);
    subjectEditionService = mock(SubjectEditionService.class);
    subjectService = new SubjectService(subjectRepository, categoryService, degreeService, subjectEditionService);

    mathematics = new Category("Mathematics");
    computerScience = new Degree("Computer Science", 3);
    physics = new Degree("Physics", 5);
    when(subjectRepository.findByCodeIgnoreCase(any())).thenReturn(Optional.empty());
    when(categoryService.getCategory(1L)).thenReturn(mathematics);
    when(categoryService.getCategory(99L)).thenThrow(new StudyMatchException(ErrorCode.NOT_FOUND));
    when(degreeService.getDegree(10L)).thenReturn(computerScience);
    when(degreeService.getDegree(20L)).thenReturn(physics);
    when(degreeService.getDegree(99L)).thenThrow(new StudyMatchException(ErrorCode.NOT_FOUND));

    // saveAndFlush returns what it was given
    when(subjectRepository.saveAndFlush(any(Subject.class))).thenAnswer(invocation -> invocation.getArgument(0));

    // the edition service's own checks are tested in SubjectEditionServiceTest, here it just builds the edition
    when(subjectEditionService.createEdition(any(Subject.class), any(SubjectEditionRequestDTO.class)))
        .thenAnswer(invocation -> new SubjectEdition(invocation.getArgument(0), new AcademicYear(2026),
            Set.of(ModelTestData.teacher())));
  }

  private static SubjectRequestDTO request(String code, DegreePlacementDTO... degrees) {
    return new SubjectRequestDTO(code, "Mathematics I", 6, 1L, List.of(degrees), EDITION);
  }

  // register subject

  @Test
  @DisplayName("A new subject is saved with its data, category and degrees")
  void newSubjectIsSaved() {
    // Act
    Subject subject = subjectService.registerSubject(
        request("MAT1", new DegreePlacementDTO(10L, 1, 1), new DegreePlacementDTO(20L, 2, 2))).getSubject();

    // Assert
    assertEquals("MAT1", subject.getCode());
    assertEquals("Mathematics I", subject.getName());
    assertEquals(6, subject.getEcts());
    assertSame(mathematics, subject.getCategory());
    assertEquals(2, subject.getDegreeSubjects().size());
    DegreeSubject inPhysics = subject.getDegreeSubjects().stream()
        .filter(ds -> ds.getDegree() == physics).findFirst().orElseThrow();
    assertEquals(2, inPhysics.getCurricularYear());
    assertEquals(2, inPhysics.getSemester());
    verify(subjectRepository).saveAndFlush(subject);
  }

  @Test
  @DisplayName("Spaces around the code and name are removed before checking and saving")
  void codeAndNameAreTrimmed() {
    // Act
    Subject subject = subjectService.registerSubject(new SubjectRequestDTO("  MAT1 ", "  Mathematics I ", 6, 1L,
        List.of(new DegreePlacementDTO(10L, 1, 1)), EDITION)).getSubject();

    // Assert
    assertEquals("MAT1", subject.getCode());
    assertEquals("Mathematics I", subject.getName());
    verify(subjectRepository).findByCodeIgnoreCase("MAT1");
  }

  @Test
  @DisplayName("A code that already exists gets SUBJECT_ALREADY_EXISTS with that subject's id, and nothing is saved")
  void duplicateSubjectIsRefused() {
    // Arrange - the id lets the frontend offer a new edition of the existing subject
    Subject existing = mock(Subject.class);
    when(existing.getId()).thenReturn(7L);
    when(subjectRepository.findByCodeIgnoreCase("MAT1")).thenReturn(Optional.of(existing));

    // Act
    StudyMatchException e = assertThrows(StudyMatchException.class,
        () -> subjectService.registerSubject(request("MAT1", new DegreePlacementDTO(10L, 1, 1))));

    // Assert
    assertEquals(ErrorCode.SUBJECT_ALREADY_EXISTS, e.getCode());
    assertEquals(Map.of("id", 7L), e.getParams());
    verify(subjectRepository, never()).saveAndFlush(any());
  }

  @Test
  @DisplayName("A category that doesn't exist gets NOT_FOUND and nothing is saved")
  void unknownCategoryIsNotFound() {
    // Act
    StudyMatchException e = assertThrows(StudyMatchException.class, () -> subjectService.registerSubject(
        new SubjectRequestDTO("MAT1", "Mathematics I", 6, 99L, List.of(new DegreePlacementDTO(10L, 1, 1)), EDITION)));

    // Assert
    assertEquals(ErrorCode.NOT_FOUND, e.getCode());
    verify(subjectRepository, never()).saveAndFlush(any());
  }

  @Test
  @DisplayName("A degree that doesn't exist gets NOT_FOUND and nothing is saved")
  void unknownDegreeIsNotFound() {
    // Act
    StudyMatchException e = assertThrows(StudyMatchException.class,
        () -> subjectService.registerSubject(request("MAT1", new DegreePlacementDTO(99L, 1, 1))));

    // Assert
    assertEquals(ErrorCode.NOT_FOUND, e.getCode());
    verify(subjectRepository, never()).saveAndFlush(any());
  }

  @Test
  @DisplayName("A curricular year past the degree's last year gets CURRICULAR_YEAR_OUT_OF_RANGE")
  void curricularYearPastTheDegreeIsRefused() {
    // Act - Computer Science has 3 curricular years
    StudyMatchException e = assertThrows(StudyMatchException.class,
        () -> subjectService.registerSubject(request("MAT1", new DegreePlacementDTO(10L, 4, 1))));

    // Assert
    assertEquals(ErrorCode.CURRICULAR_YEAR_OUT_OF_RANGE, e.getCode());
    verify(subjectRepository, never()).saveAndFlush(any());
  }

  @Test
  @DisplayName("The degree's last curricular year is allowed")
  void lastCurricularYearIsAllowed() {
    // Act
    Subject subject = subjectService.registerSubject(request("MAT1", new DegreePlacementDTO(10L, 3, 2))).getSubject();

    // Assert
    assertEquals(3, subject.getDegreeSubjects().iterator().next().getCurricularYear());
  }

  @Test
  @DisplayName("The same degree twice gets SUBJECT_ALREADY_IN_DEGREE")
  void sameDegreeTwiceIsRefused() {
    // Act
    StudyMatchException e = assertThrows(StudyMatchException.class, () -> subjectService.registerSubject(
        request("MAT1", new DegreePlacementDTO(10L, 1, 1), new DegreePlacementDTO(10L, 2, 1))));

    // Assert
    assertEquals(ErrorCode.SUBJECT_ALREADY_IN_DEGREE, e.getCode());
    verify(subjectRepository, never()).saveAndFlush(any());
  }

  @Test
  @DisplayName("If someone else saves the same code first, the unique column's error becomes SUBJECT_ALREADY_EXISTS")
  void simultaneousDuplicateIsRefused() {
    // Arrange - the check passed, but by the time we save another Staff already registered it
    when(subjectRepository.saveAndFlush(any(Subject.class))).thenThrow(new DataIntegrityViolationException("unique code"));

    // Act
    StudyMatchException e = assertThrows(StudyMatchException.class,
        () -> subjectService.registerSubject(request("MAT1", new DegreePlacementDTO(10L, 1, 1))));

    // Assert
    assertEquals(ErrorCode.SUBJECT_ALREADY_EXISTS, e.getCode());
  }

  @Test
  @DisplayName("The subject's first edition is created from the request, for the saved subject")
  void firstEditionIsCreated() {
    // Act
    SubjectEdition edition = subjectService.registerSubject(request("MAT1", new DegreePlacementDTO(10L, 1, 1)));

    // Assert
    verify(subjectEditionService).createEdition(edition.getSubject(), EDITION);
    assertEquals("MAT1", edition.getSubject().getCode());
  }

  @Test
  @DisplayName("When the edition is refused, its error goes through (and the transaction drops the subject)")
  void refusedEditionIsPassedOn() {
    // Arrange
    when(subjectEditionService.createEdition(any(Subject.class), any(SubjectEditionRequestDTO.class)))
        .thenThrow(new StudyMatchException(ErrorCode.NOT_FOUND));

    // Act
    StudyMatchException e = assertThrows(StudyMatchException.class,
        () -> subjectService.registerSubject(request("MAT1", new DegreePlacementDTO(10L, 1, 1))));

    // Assert
    assertEquals(ErrorCode.NOT_FOUND, e.getCode());
  }

  @Test
  @DisplayName("A refused subject never gets to its edition")
  void refusedSubjectCreatesNoEdition() {
    // Arrange
    Subject existing = mock(Subject.class);
    when(existing.getId()).thenReturn(7L);
    when(subjectRepository.findByCodeIgnoreCase("MAT1")).thenReturn(Optional.of(existing));

    // Act
    assertThrows(StudyMatchException.class,
        () -> subjectService.registerSubject(request("MAT1", new DegreePlacementDTO(10L, 1, 1))));

    // Assert
    verify(subjectEditionService, never()).createEdition(any(), any());
  }

  // new edition of an existing subject

  @Test
  @DisplayName("A new edition of an existing subject is created for that subject")
  void editionOfExistingSubject() {
    // Arrange
    Subject existing = new Subject("MAT1", "Mathematics I", 6, mathematics);
    when(subjectRepository.findWithDegreesById(5L)).thenReturn(Optional.of(existing));

    // Act
    SubjectEdition edition = subjectService.registerEdition(5L, EDITION);

    // Assert
    assertSame(existing, edition.getSubject());
    verify(subjectEditionService).createEdition(existing, EDITION);
  }

  @Test
  @DisplayName("A new edition of a subject that doesn't exist gets NOT_FOUND")
  void editionOfUnknownSubject() {
    // Arrange
    when(subjectRepository.findWithDegreesById(99L)).thenReturn(Optional.empty());

    // Act
    StudyMatchException e = assertThrows(StudyMatchException.class, () -> subjectService.registerEdition(99L, EDITION));

    // Assert
    assertEquals(ErrorCode.NOT_FOUND, e.getCode());
    verify(subjectEditionService, never()).createEdition(any(), any());
  }

  // list subjects

  @Test
  @DisplayName("Listing returns the subjects in the order the repository sorted them (by code)")
  void listReturnsSortedSubjects() {
    // Arrange
    List<Subject> sorted = List.of(new Subject("ALG1", "Algorithms", 6, mathematics),
        new Subject("MAT1", "Mathematics I", 6, mathematics));
    when(subjectRepository.findAllByOrderByCodeAsc()).thenReturn(sorted);

    // Act
    List<Subject> result = subjectService.listSubjects();

    // Assert
    assertEquals(sorted, result);
  }

  // get one subject

  @Test
  @DisplayName("Getting a subject by id returns it")
  void getSubjectById() {
    // Arrange
    Subject subject = new Subject("MAT1", "Mathematics I", 6, mathematics);
    when(subjectRepository.findWithDegreesById(1L)).thenReturn(Optional.of(subject));

    // Act
    Subject result = subjectService.getSubject(1L);

    // Assert
    assertEquals(subject, result);
  }

  @Test
  @DisplayName("An id with no subject gets NOT_FOUND")
  void unknownIdIsNotFound() {
    // Arrange
    when(subjectRepository.findWithDegreesById(99L)).thenReturn(Optional.empty());

    // Act
    StudyMatchException e = assertThrows(StudyMatchException.class, () -> subjectService.getSubject(99L));

    // Assert
    assertEquals(ErrorCode.NOT_FOUND, e.getCode());
  }

}
