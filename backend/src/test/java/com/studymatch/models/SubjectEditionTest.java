package com.studymatch.models;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.studymatch.exceptions.ErrorCode;
import com.studymatch.exceptions.StudyMatchException;

class SubjectEditionTest {

  private SubjectEdition edition;

  @BeforeEach
  void setUp() {
    edition = ModelTestData.subjectEdition();
  }

  // teachers (at least one)

  @Test
  @DisplayName("An edition can't be created without teachers")
  void cannotCreateEditionWithoutTeachers() {
    // Act
    StudyMatchException e = assertThrows(StudyMatchException.class,
        () -> new SubjectEdition(ModelTestData.subject(), new AcademicYear(2026), Set.of()));

    // Assert
    assertEquals(ErrorCode.AT_LEAST_ONE_TEACHER_REQUIRED, e.getCode());
  }

  @Test
  @DisplayName("Removing every teacher is rejected")
  void emptyTeachersAreRejected() {
    // Act
    StudyMatchException e = assertThrows(StudyMatchException.class, () -> edition.setTeachers(Set.of()));

    // Assert
    assertEquals(ErrorCode.AT_LEAST_ONE_TEACHER_REQUIRED, e.getCode());
  }

  @Test
  @DisplayName("Setting teachers replaces the previous ones")
  void setTeachersReplacesPreviousTeachers() {
    // Arrange - a single teacher, right at the "at least one" limit
    Teacher newTeacher = new Teacher("new@example.com", "new.teacher@test.com", "New Teacher", "hash");

    // Act
    edition.setTeachers(Set.of(newTeacher));

    // Assert
    assertEquals(Set.of(newTeacher), edition.getTeachers());
  }

  // class sections

  @Test
  @DisplayName("Adding a class links it to this edition")
  void addClassSectionLinksItToEdition() {
    // Act
    edition.addClassSection("T1");

    // Assert
    ClassSection classSection = edition.getClassSections().iterator().next();
    assertEquals("T1", classSection.getCode());
    assertSame(edition, classSection.getSubjectEdition());
  }

  @Test
  @DisplayName("An edition can have several classes with different codes")
  void severalClassesWithDifferentCodes() {
    // Act
    edition.addClassSection("T1");
    edition.addClassSection("PL1");

    // Assert
    assertEquals(2, edition.getClassSections().size());
  }

  @Test
  @DisplayName("Two classes can't have the same code in one edition")
  void duplicateClassCodeIsRejected() {
    // Arrange
    edition.addClassSection("T1");

    // Act
    StudyMatchException e = assertThrows(StudyMatchException.class, () -> edition.addClassSection("T1"));

    // Assert
    assertEquals(ErrorCode.CLASS_SECTION_ALREADY_EXISTS, e.getCode());
  }

  @Test
  @DisplayName("A class is found by its code")
  void classSectionFoundByCode() {
    // Arrange
    edition.addClassSection("T1");
    edition.addClassSection("PL1");

    // Act
    ClassSection found = edition.getClassSection("PL1");

    // Assert
    assertEquals("PL1", found.getCode());
    assertSame(edition, found.getSubjectEdition());
  }

  @Test
  @DisplayName("A code the edition doesn't have gets CLASS_SECTION_NOT_IN_SUBJECT_EDITION, codes are exact")
  void unknownClassSectionIsRefused() {
    // Arrange
    edition.addClassSection("T1");

    // Act
    StudyMatchException e = assertThrows(StudyMatchException.class, () -> edition.getClassSection("t1"));

    // Assert
    assertEquals(ErrorCode.CLASS_SECTION_NOT_IN_SUBJECT_EDITION, e.getCode());
  }

  // teacher of a class section

  @Test
  @DisplayName("A new class has no teacher yet")
  void newClassHasNoTeacher() {
    // Act
    edition.addClassSection("T1");

    // Assert
    assertNull(edition.getClassSection("T1").getTeacher());
  }

  @Test
  @DisplayName("One of the edition's teachers can teach a class, and only that class gets them")
  void editionTeacherTeachesClass() {
    // Arrange
    Teacher teacher = edition.getTeachers().iterator().next();
    edition.addClassSection("T1");
    edition.addClassSection("T2");

    // Act
    ClassSection assigned = edition.assignTeacher("T1", teacher);

    // Assert
    assertSame(edition.getClassSection("T1"), assigned);
    assertSame(teacher, assigned.getTeacher());
    assertNull(edition.getClassSection("T2").getTeacher());
  }

  @Test
  @DisplayName("Picking another of the edition's teachers replaces the class's teacher")
  void classTeacherIsReplaced() {
    // Arrange
    Teacher first = edition.getTeachers().iterator().next();
    Teacher second = new Teacher("second@example.com", "second@test.com", "Second Teacher", "hash");
    edition.setTeachers(Set.of(first, second));
    edition.addClassSection("T1");
    edition.assignTeacher("T1", first);

    // Act
    edition.assignTeacher("T1", second);

    // Assert
    assertSame(second, edition.getClassSection("T1").getTeacher());
  }

  @Test
  @DisplayName("A teacher who isn't one of the edition's gets TEACHER_NOT_IN_SUBJECT_EDITION, the class keeps no teacher")
  void outsideTeacherIsRefused() {
    // Arrange
    edition.addClassSection("T1");
    Teacher outsider = new Teacher("out@example.com", "out@test.com", "Outsider", "hash");

    // Act
    StudyMatchException e = assertThrows(StudyMatchException.class, () -> edition.assignTeacher("T1", outsider));

    // Assert
    assertEquals(ErrorCode.TEACHER_NOT_IN_SUBJECT_EDITION, e.getCode());
    assertNull(edition.getClassSection("T1").getTeacher());
  }

  @Test
  @DisplayName("A class the edition doesn't have gets CLASS_SECTION_NOT_IN_SUBJECT_EDITION")
  void teacherOfUnknownClassIsRefused() {
    // Arrange
    Teacher teacher = edition.getTeachers().iterator().next();

    // Act
    StudyMatchException e = assertThrows(StudyMatchException.class, () -> edition.assignTeacher("T9", teacher));

    // Assert
    assertEquals(ErrorCode.CLASS_SECTION_NOT_IN_SUBJECT_EDITION, e.getCode());
  }

  @Test
  @DisplayName("Removing a teacher who still teaches classes gets TEACHER_STILL_TEACHES_SECTION naming them sorted, and the teachers stay")
  void removingTeacherOfClassIsRefused() {
    // Arrange
    Teacher first = edition.getTeachers().iterator().next();
    Teacher second = new Teacher("second@example.com", "second@test.com", "Second Teacher", "hash");
    edition.setTeachers(Set.of(first, second));
    edition.addClassSection("T2");
    edition.addClassSection("T1");
    edition.addClassSection("T3");
    edition.assignTeacher("T2", first);
    edition.assignTeacher("T1", first);
    edition.assignTeacher("T3", second);

    // Act
    StudyMatchException e = assertThrows(StudyMatchException.class, () -> edition.setTeachers(Set.of(second)));

    // Assert
    assertEquals(ErrorCode.TEACHER_STILL_TEACHES_SECTION, e.getCode());
    assertEquals(Map.of("classSections", "T1, T2"), e.getParams());
    assertEquals(Set.of(first, second), edition.getTeachers());
  }

  @Test
  @DisplayName("Keeping a class's teacher while adding others is fine")
  void keepingClassTeacherIsAllowed() {
    // Arrange
    Teacher first = edition.getTeachers().iterator().next();
    Teacher second = new Teacher("second@example.com", "second@test.com", "Second Teacher", "hash");
    edition.addClassSection("T1");
    edition.assignTeacher("T1", first);

    // Act
    edition.setTeachers(Set.of(first, second));

    // Assert
    assertEquals(Set.of(first, second), edition.getTeachers());
    assertSame(first, edition.getClassSection("T1").getTeacher());
  }

  @Test
  @DisplayName("Removing a teacher who teaches no class is fine, even when other classes have no teacher")
  void removingTeacherWithoutClassesIsAllowed() {
    // Arrange
    Teacher first = edition.getTeachers().iterator().next();
    Teacher second = new Teacher("second@example.com", "second@test.com", "Second Teacher", "hash");
    edition.setTeachers(Set.of(first, second));
    edition.addClassSection("T1");
    edition.addClassSection("T2");
    edition.assignTeacher("T1", first);

    // Act
    edition.setTeachers(Set.of(first));

    // Assert
    assertEquals(Set.of(first), edition.getTeachers());
  }

}
