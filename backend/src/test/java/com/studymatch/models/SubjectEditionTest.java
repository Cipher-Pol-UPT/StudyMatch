package com.studymatch.models;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

}
