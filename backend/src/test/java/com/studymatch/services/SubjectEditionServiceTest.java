package com.studymatch.services;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
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
import com.studymatch.repositories.SubjectEditionRepository;

class SubjectEditionServiceTest {

  private SubjectEditionRepository subjectEditionRepository;
  private AcademicYearService academicYearService;
  private TeacherService teacherService;
  private SubjectEditionService subjectEditionService;

  private Subject subject;
  private AcademicYear year2026;
  private Teacher ana;
  private Teacher rui;

  @BeforeEach
  void setUp() {
    subjectEditionRepository = mock(SubjectEditionRepository.class);
    academicYearService = mock(AcademicYearService.class);
    teacherService = mock(TeacherService.class);
    subjectEditionService = new SubjectEditionService(subjectEditionRepository, academicYearService, teacherService);

    subject = ModelTestData.subject();
    year2026 = new AcademicYear(2026);
    ana = new Teacher("ana@example.com", "ana@studymatch.com", "Ana", "hash");
    rui = new Teacher("rui@example.com", "rui@studymatch.com", "Rui", "hash");
    when(academicYearService.getAcademicYear(2026)).thenReturn(year2026);
    when(academicYearService.getAcademicYear(2099)).thenThrow(new StudyMatchException(ErrorCode.NOT_FOUND));
    when(teacherService.getTeachers(List.of(1L))).thenReturn(Set.of(ana));
    when(teacherService.getTeachers(List.of(1L, 2L))).thenReturn(Set.of(ana, rui));
    when(teacherService.getTeachers(List.of())).thenReturn(Set.of());

    // saveAndFlush returns what it was given
    when(subjectEditionRepository.saveAndFlush(any(SubjectEdition.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
  }

  private static SubjectEditionRequestDTO request(List<Long> teacherNumbers, List<String> classSections) {
    return new SubjectEditionRequestDTO(2026, teacherNumbers, classSections);
  }

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
  @DisplayName("No teachers gets AT_LEAST_ONE_TEACHER_REQUIRED from the edition, and nothing is saved")
  void noTeachersIsRefused() {
    // Act
    StudyMatchException e = assertThrows(StudyMatchException.class,
        () -> subjectEditionService.createEdition(subject, request(List.of(), List.of())));

    // Assert
    assertEquals(ErrorCode.AT_LEAST_ONE_TEACHER_REQUIRED, e.getCode());
    verify(subjectEditionRepository, never()).saveAndFlush(any());
  }

  @Test
  @DisplayName("An academic year that isn't registered: the year's NOT_FOUND goes through and nothing is saved")
  void unknownAcademicYearIsNotFound() {
    // Act
    StudyMatchException e = assertThrows(StudyMatchException.class, () -> subjectEditionService.createEdition(subject,
        new SubjectEditionRequestDTO(2099, List.of(1L), List.of())));

    // Assert
    assertEquals(ErrorCode.NOT_FOUND, e.getCode());
    verify(subjectEditionRepository, never()).saveAndFlush(any());
  }

  @Test
  @DisplayName("A teacher number that doesn't exist: the teachers' NOT_FOUND goes through and nothing is saved")
  void unknownTeacherIsNotFound() {
    // Arrange
    when(teacherService.getTeachers(List.of(1L, 99L))).thenThrow(new StudyMatchException(ErrorCode.NOT_FOUND));

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

  // who can see an edition's students

  @Test
  @DisplayName("A teacher of the edition can see its students")
  void teacherOfEditionAllowed() {
    // Arrange
    when(subjectEditionRepository.existsById(7L)).thenReturn(true);
    when(subjectEditionRepository.existsByIdAndTeachers_Number(7L, 1L)).thenReturn(true);

    // Act + Assert
    assertDoesNotThrow(() -> subjectEditionService.checkTaughtBy(7L, 1L));
  }

  @Test
  @DisplayName("Any other teacher gets ACCESS_DENIED")
  void otherTeacherDenied() {
    // Arrange - the edition exists, but teacher 2 doesn't teach it
    when(subjectEditionRepository.existsById(7L)).thenReturn(true);

    // Act
    StudyMatchException e = assertThrows(StudyMatchException.class, () -> subjectEditionService.checkTaughtBy(7L, 2L));

    // Assert
    assertEquals(ErrorCode.ACCESS_DENIED, e.getCode());
  }

  @Test
  @DisplayName("An edition that doesn't exist is NOT_FOUND")
  void unknownEdition() {
    StudyMatchException e = assertThrows(StudyMatchException.class, () -> subjectEditionService.checkTaughtBy(99L, 1L));
    assertEquals(ErrorCode.NOT_FOUND, e.getCode());
  }

  @Test
  @DisplayName("Teacher editions are returned as complete DTOs, newest academic year first")
  void listTaughtEditions() {
    // Arrange
    SubjectEdition older = new SubjectEdition(subject, new AcademicYear(2025), Set.of(ana));
    older.addClassSection("T1");
    SubjectEdition newer = new SubjectEdition(subject, year2026, Set.of(ana));
    newer.addClassSection("T2");
    when(subjectEditionRepository.findDistinctByTeachers_Number(7L)).thenReturn(List.of(older, newer));

    // Act
    var editions = subjectEditionService.listTaughtBy(7L);

    // Assert
    assertEquals(List.of(2026, 2025), editions.stream().map(edition -> edition.academicYear()).toList());
    assertEquals(List.of("T2"), editions.getFirst().classSections());
    assertEquals(subject.getCode(), editions.getFirst().subject().code());
    verify(subjectEditionRepository).findDistinctByTeachers_Number(7L);
  }

  @Test
  @DisplayName("A teacher without assigned editions gets an empty list")
  void noTaughtEditions() {
    // Arrange
    when(subjectEditionRepository.findDistinctByTeachers_Number(7L)).thenReturn(List.of());

    // Act
    var editions = subjectEditionService.listTaughtBy(7L);

    // Assert
    assertEquals(List.of(), editions);
  }

}
