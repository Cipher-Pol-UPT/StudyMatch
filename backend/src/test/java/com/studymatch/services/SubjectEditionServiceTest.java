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
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import com.studymatch.dtos.SubjectEditionRequestDTO;
import com.studymatch.exceptions.ErrorCode;
import com.studymatch.exceptions.StudyMatchException;
import com.studymatch.models.AcademicYear;
import com.studymatch.models.ClassSection;
import com.studymatch.models.ModelTestData;
import com.studymatch.models.Subject;
import com.studymatch.models.SubjectEdition;
import com.studymatch.models.Teacher;
import com.studymatch.repositories.AcademicYearRepository;
import com.studymatch.repositories.SubjectEditionRepository;
import com.studymatch.repositories.SubjectRepository;
import com.studymatch.repositories.TeacherRepository;

class SubjectEditionServiceTest {

  private SubjectEditionRepository subjectEditionRepository;
  private SubjectRepository subjectRepository;
  private AcademicYearRepository academicYearRepository;
  private TeacherRepository teacherRepository;
  private SubjectEditionService subjectEditionService;

  private Subject subject;
  private AcademicYear year2026;
  private Teacher ana;
  private Teacher rui;

  @BeforeEach
  void setUp() {
    subjectEditionRepository = mock(SubjectEditionRepository.class);
    subjectRepository = mock(SubjectRepository.class);
    academicYearRepository = mock(AcademicYearRepository.class);
    teacherRepository = mock(TeacherRepository.class);
    subjectEditionService = new SubjectEditionService(subjectEditionRepository, subjectRepository,
        academicYearRepository, teacherRepository);

    subject = ModelTestData.subject();
    year2026 = new AcademicYear(2026);
    ana = new Teacher("ana@example.com", "ana@studymatch.com", "Ana", "hash");
    rui = new Teacher("rui@example.com", "rui@studymatch.com", "Rui", "hash");
    when(academicYearRepository.findById(2026)).thenReturn(Optional.of(year2026));
    when(teacherRepository.findAllById(Set.of(1L))).thenReturn(List.of(ana));
    when(teacherRepository.findAllById(Set.of(1L, 2L))).thenReturn(List.of(ana, rui));

    // saveAndFlush returns what it was given
    when(subjectEditionRepository.saveAndFlush(any(SubjectEdition.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
  }

  private static SubjectEditionRequestDTO request(List<Long> teacherIds, List<String> classSections) {
    return new SubjectEditionRequestDTO(2026, teacherIds, classSections);
  }

  // create edition

  @Test
  @DisplayName("A new edition is saved for the subject and year, with its teachers and classes")
  void newEditionIsSaved() {
    // Act
    SubjectEdition edition = subjectEditionService.createEdition(subject, request(List.of(1L, 2L), List.of("T1", " PL1 ")));

    // Assert
    assertSame(subject, edition.getSubject());
    assertSame(year2026, edition.getAcademicYear());
    assertEquals(Set.of(ana, rui), edition.getTeachers());
    assertEquals(Set.of("T1", "PL1"),
        edition.getClassSections().stream().map(ClassSection::getCode).collect(Collectors.toSet()));
    verify(subjectEditionRepository).saveAndFlush(edition);
  }

  @Test
  @DisplayName("Classes are optional: an edition without them is saved")
  void editionWithoutClasses() {
    // Act
    SubjectEdition edition = subjectEditionService.createEdition(subject, request(List.of(1L), null));

    // Assert
    assertEquals(Set.of(), edition.getClassSections());
  }

  @Test
  @DisplayName("The same teacher picked twice is assigned once")
  void repeatedTeacherCountsOnce() {
    // Act
    SubjectEdition edition = subjectEditionService.createEdition(subject, request(List.of(1L, 1L), List.of()));

    // Assert
    assertEquals(Set.of(ana), edition.getTeachers());
  }

  @Test
  @DisplayName("An academic year that isn't registered gets NOT_FOUND and nothing is saved")
  void unknownAcademicYearIsNotFound() {
    // Act
    StudyMatchException e = assertThrows(StudyMatchException.class, () -> subjectEditionService.createEdition(subject,
        new SubjectEditionRequestDTO(2099, List.of(1L), List.of())));

    // Assert
    assertEquals(ErrorCode.NOT_FOUND, e.getCode());
    verify(subjectEditionRepository, never()).saveAndFlush(any());
  }

  @Test
  @DisplayName("A teacher that doesn't exist gets NOT_FOUND and nothing is saved")
  void unknownTeacherIsNotFound() {
    // Arrange - only teacher 1 exists
    when(teacherRepository.findAllById(Set.of(1L, 99L))).thenReturn(List.of(ana));

    // Act
    StudyMatchException e = assertThrows(StudyMatchException.class,
        () -> subjectEditionService.createEdition(subject, request(List.of(1L, 99L), List.of())));

    // Assert
    assertEquals(ErrorCode.NOT_FOUND, e.getCode());
    verify(subjectEditionRepository, never()).saveAndFlush(any());
  }

  @Test
  @DisplayName("A subject that already has an edition that year gets SUBJECT_EDITION_ALREADY_EXISTS")
  void secondEditionSameYearIsRefused() {
    // Arrange
    when(subjectEditionRepository.existsBySubjectAndAcademicYear(subject, year2026)).thenReturn(true);

    // Act
    StudyMatchException e = assertThrows(StudyMatchException.class,
        () -> subjectEditionService.createEdition(subject, request(List.of(1L), List.of())));

    // Assert
    assertEquals(ErrorCode.SUBJECT_EDITION_ALREADY_EXISTS, e.getCode());
    verify(subjectEditionRepository, never()).saveAndFlush(any());
  }

  @Test
  @DisplayName("The same class code twice gets CLASS_SECTION_ALREADY_EXISTS")
  void repeatedClassCodeIsRefused() {
    // Act - spaces don't make it a different class
    StudyMatchException e = assertThrows(StudyMatchException.class,
        () -> subjectEditionService.createEdition(subject, request(List.of(1L), List.of("T1", " T1"))));

    // Assert
    assertEquals(ErrorCode.CLASS_SECTION_ALREADY_EXISTS, e.getCode());
    verify(subjectEditionRepository, never()).saveAndFlush(any());
  }

  @Test
  @DisplayName("If someone else creates the same edition first, the unique constraint's error becomes SUBJECT_EDITION_ALREADY_EXISTS")
  void simultaneousEditionIsRefused() {
    // Arrange
    when(subjectEditionRepository.saveAndFlush(any(SubjectEdition.class)))
        .thenThrow(new DataIntegrityViolationException("unique subject and year"));

    // Act
    StudyMatchException e = assertThrows(StudyMatchException.class,
        () -> subjectEditionService.createEdition(subject, request(List.of(1L), List.of())));

    // Assert
    assertEquals(ErrorCode.SUBJECT_EDITION_ALREADY_EXISTS, e.getCode());
  }

  // edition of an existing subject

  @Test
  @DisplayName("An edition for an existing subject is created for that subject")
  void editionOfExistingSubject() {
    // Arrange
    when(subjectRepository.findWithDegreesById(5L)).thenReturn(Optional.of(subject));

    // Act
    SubjectEdition edition = subjectEditionService.registerEdition(5L, request(List.of(1L), List.of()));

    // Assert
    assertSame(subject, edition.getSubject());
  }

  @Test
  @DisplayName("An edition for a subject that doesn't exist gets NOT_FOUND")
  void editionOfUnknownSubject() {
    // Arrange
    when(subjectRepository.findWithDegreesById(99L)).thenReturn(Optional.empty());

    // Act
    StudyMatchException e = assertThrows(StudyMatchException.class,
        () -> subjectEditionService.registerEdition(99L, request(List.of(1L), List.of())));

    // Assert
    assertEquals(ErrorCode.NOT_FOUND, e.getCode());
  }

}
