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
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import com.studymatch.dtos.DegreePlacementDTO;
import com.studymatch.dtos.SubjectRequestDTO;
import com.studymatch.exceptions.ErrorCode;
import com.studymatch.exceptions.StudyMatchException;
import com.studymatch.models.Category;
import com.studymatch.models.Degree;
import com.studymatch.models.DegreeSubject;
import com.studymatch.models.Subject;
import com.studymatch.repositories.CategoryRepository;
import com.studymatch.repositories.DegreeRepository;
import com.studymatch.repositories.SubjectRepository;

class SubjectServiceTest {

  private SubjectRepository subjectRepository;
  private CategoryRepository categoryRepository;
  private DegreeRepository degreeRepository;
  private SubjectService subjectService;

  private Category mathematics;
  private Degree computerScience;
  private Degree physics;

  @BeforeEach
  void setUp() {
    subjectRepository = mock(SubjectRepository.class);
    categoryRepository = mock(CategoryRepository.class);
    degreeRepository = mock(DegreeRepository.class);
    subjectService = new SubjectService(subjectRepository, categoryRepository, degreeRepository);

    mathematics = new Category("Mathematics");
    computerScience = new Degree("Computer Science", 3);
    physics = new Degree("Physics", 5);
    when(categoryRepository.findById(1L)).thenReturn(Optional.of(mathematics));
    when(degreeRepository.findById(10L)).thenReturn(Optional.of(computerScience));
    when(degreeRepository.findById(20L)).thenReturn(Optional.of(physics));

    // saveAndFlush returns what it was given
    when(subjectRepository.saveAndFlush(any(Subject.class))).thenAnswer(invocation -> invocation.getArgument(0));
  }

  private static SubjectRequestDTO request(String code, DegreePlacementDTO... degrees) {
    return new SubjectRequestDTO(code, "Mathematics I", 6, 1L, List.of(degrees));
  }

  // register subject

  @Test
  @DisplayName("A new subject is saved with its data, category and degrees")
  void newSubjectIsSaved() {
    // Act
    Subject subject = subjectService.registerSubject(
        request("MAT1", new DegreePlacementDTO(10L, 1, 1), new DegreePlacementDTO(20L, 2, 2)));

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
    Subject subject = subjectService.registerSubject(
        new SubjectRequestDTO("  MAT1 ", "  Mathematics I ", 6, 1L, List.of(new DegreePlacementDTO(10L, 1, 1))));

    // Assert
    assertEquals("MAT1", subject.getCode());
    assertEquals("Mathematics I", subject.getName());
    verify(subjectRepository).existsByCodeIgnoreCase("MAT1");
  }

  @Test
  @DisplayName("A subject with a code that already exists gets SUBJECT_ALREADY_EXISTS and isn't saved")
  void duplicateSubjectIsRefused() {
    // Arrange
    when(subjectRepository.existsByCodeIgnoreCase("MAT1")).thenReturn(true);

    // Act
    StudyMatchException e = assertThrows(StudyMatchException.class,
        () -> subjectService.registerSubject(request("MAT1", new DegreePlacementDTO(10L, 1, 1))));

    // Assert
    assertEquals(ErrorCode.SUBJECT_ALREADY_EXISTS, e.getCode());
    verify(subjectRepository, never()).saveAndFlush(any());
  }

  @Test
  @DisplayName("A category that doesn't exist gets NOT_FOUND and nothing is saved")
  void unknownCategoryIsNotFound() {
    // Act
    StudyMatchException e = assertThrows(StudyMatchException.class, () -> subjectService.registerSubject(
        new SubjectRequestDTO("MAT1", "Mathematics I", 6, 99L, List.of(new DegreePlacementDTO(10L, 1, 1)))));

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
    Subject subject = subjectService.registerSubject(request("MAT1", new DegreePlacementDTO(10L, 3, 2)));

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
