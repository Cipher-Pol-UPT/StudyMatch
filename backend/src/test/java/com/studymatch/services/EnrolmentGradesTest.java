package com.studymatch.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.studymatch.dtos.CategoryLevelDTO;
import com.studymatch.dtos.EditionStudentDTO;
import com.studymatch.enums.StatuteType;
import com.studymatch.models.AcademicYear;
import com.studymatch.models.Category;
import com.studymatch.models.ClassSection;
import com.studymatch.models.Enrolment;
import com.studymatch.models.ModelTestData;
import com.studymatch.models.Student;
import com.studymatch.models.Subject;
import com.studymatch.models.SubjectEdition;
import com.studymatch.repositories.EnrolmentRepository;

// the grade and attendance numbers behind View Students
class EnrolmentGradesTest {

  private EnrolmentRepository enrolmentRepository;
  private LessonService lessonService;
  private AbsenceService absenceService;
  private EnrolmentService enrolmentService;

  private Student ana;
  private Category programming;
  private Category maths;

  @BeforeEach
  void setUp() {
    enrolmentRepository = mock(EnrolmentRepository.class);
    lessonService = mock(LessonService.class);
    absenceService = mock(AbsenceService.class);
    enrolmentService = new EnrolmentService(enrolmentRepository, lessonService, absenceService);

    ana = ModelTestData.student("Ana Test");
    ReflectionTestUtils.setField(ana, "number", 50001L);
    // ids matter: levels are grouped by category id
    programming = category(1L, "Programming");
    maths = category(2L, "Maths");
  }

  private static Category category(Long id, String name) {
    Category category = new Category(name);
    ReflectionTestUtils.setField(category, "id", id);
    return category;
  }

  // an enrolment in its own subject; finalGrade null = still in progress
  private static Enrolment enrolment(Student student, Category category, int ects, Integer finalGrade, String classCode) {
    Subject subject = new Subject("S" + System.nanoTime(), "Test Subject", ects, category);
    SubjectEdition edition = new SubjectEdition(subject, new AcademicYear(2026), Set.of(ModelTestData.teacher()));
    edition.addClassSection(classCode);
    ClassSection section = edition.getClassSections().iterator().next();
    Enrolment enrolment = new Enrolment(student, section);
    if (finalGrade != null) {
      enrolment.setFinalGrade(finalGrade);
    }
    return enrolment;
  }

  private Enrolment enrolment(Category category, int ects, Integer finalGrade) {
    return enrolment(ana, category, ects, finalGrade, "T1");
  }

  private void anaHas(Enrolment... enrolments) {
    when(enrolmentRepository.findByStudent(ana)).thenReturn(List.of(enrolments));
  }

  // finished

  @Test
  @DisplayName("Only enrolments with a final grade are finished, failed ones too")
  void finishedMeansGraded() {
    assertTrue(enrolment(programming, 6, 7).isFinished());
    assertTrue(enrolment(programming, 6, 15).isFinished());
    assertEquals(false, enrolment(programming, 6, null).isFinished());
  }

  // average grade

  @Test
  @DisplayName("The average is weighted by ECTS: 14 in 10 ECTS and 18 in 5 ECTS is 15.33, not 16")
  void averageWeightedByEcts() {
    // Arrange
    anaHas(enrolment(programming, 10, 14), enrolment(maths, 5, 18));

    // Act
    Double average = enrolmentService.getAverageGrade(ana);

    // Assert - (14*10 + 18*5) / 15
    assertEquals(230.0 / 15, average, 0.0001);
  }

  @Test
  @DisplayName("Failed subjects count, subjects still in progress don't")
  void averageCountsFailedNotInProgress() {
    // Arrange - the in-progress one would drag it down to 0 if it counted
    anaHas(enrolment(programming, 6, 8), enrolment(maths, 6, 16), enrolment(maths, 6, null));

    // Act + Assert
    assertEquals(12.0, enrolmentService.getAverageGrade(ana), 0.0001);
  }

  @Test
  @DisplayName("Nothing finished yet means no average (null), not 0")
  void noAverageYet() {
    anaHas(enrolment(programming, 6, null));
    assertNull(enrolmentService.getAverageGrade(ana));
  }

  // category levels

  @Test
  @DisplayName("Each category's level is the plain average of its finished subjects, sorted by name")
  void categoryLevels() {
    // Arrange - different ECTS on purpose: levels ignore them
    anaHas(enrolment(programming, 10, 12), enrolment(programming, 5, 16), enrolment(maths, 6, 10),
        enrolment(maths, 6, null));

    // Act
    List<CategoryLevelDTO> levels = enrolmentService.getCategoryLevels(ana);

    // Assert - Maths before Programming, and the in-progress maths subject left out
    assertEquals(List.of(new CategoryLevelDTO(2L, "Maths", 10.0), new CategoryLevelDTO(1L, "Programming", 14.0)),
        levels);
  }

  @Test
  @DisplayName("A category with nothing finished has no level")
  void noLevelYet() {
    // Arrange
    anaHas(enrolment(programming, 6, 15));

    // Act + Assert
    assertTrue(enrolmentService.getCategoryLevels(ana).stream().noneMatch(level -> level.categoryId().equals(2L)));
    assertNull(enrolmentService.getCategoryLevel(ana, 2L));
    assertEquals(15.0, enrolmentService.getCategoryLevel(ana, 1L));
  }

  // attendance in one subject

  @Test
  @DisplayName("Attendance in one subject only counts that subject's lessons")
  void subjectAttendance() {
    // Arrange
    Enrolment enrolment = enrolment(programming, 6, null);
    when(lessonService.countLessons(enrolment.getClassSection())).thenReturn(20L);
    when(absenceService.countUnjustifiedAbsences(enrolment)).thenReturn(5L);

    // Act + Assert
    assertEquals(75.0, enrolmentService.getSubjectAttendanceRate(enrolment));
  }

  @Test
  @DisplayName("Before the first lesson there's no attendance yet (null)")
  void subjectAttendanceBeforeFirstLesson() {
    Enrolment enrolment = enrolment(programming, 6, null);
    assertNull(enrolmentService.getSubjectAttendanceRate(enrolment));
  }

  // the teacher's list

  private List<Enrolment> editionWithThreeStudents() {
    Student rui = ModelTestData.student("Rui Test");
    ReflectionTestUtils.setField(rui, "number", 50002L);
    Student eva = ModelTestData.student("Eva Test");
    ReflectionTestUtils.setField(eva, "number", 50003L);
    when(enrolmentRepository.findByStudent(any())).thenReturn(List.of());
    List<Enrolment> enrolments = List.of(enrolment(rui, programming, 6, null, "T1"),
        enrolment(ana, programming, 6, null, "T2"), enrolment(eva, programming, 6, null, "T1"));
    when(enrolmentRepository.findBySubjectEditionId(7L)).thenReturn(enrolments);
    return enrolments;
  }

  private List<String> names(List<EditionStudentDTO> rows) {
    return rows.stream().map(EditionStudentDTO::name).toList();
  }

  @Test
  @DisplayName("Without filters everyone enrolled comes back, sorted by name")
  void editionAllStudents() {
    editionWithThreeStudents();
    assertEquals(List.of("Ana Test", "Eva Test", "Rui Test"), names(enrolmentService.listStudentsOfEdition(7L, null, null)));
  }

  @Test
  @DisplayName("Empty filters count as no filter")
  void editionEmptyFilters() {
    editionWithThreeStudents();
    assertEquals(3, enrolmentService.listStudentsOfEdition(7L, " ", "").size());
  }

  @Test
  @DisplayName("Filtering by class ignores capitals")
  void editionByClass() {
    editionWithThreeStudents();
    assertEquals(List.of("Eva Test", "Rui Test"), names(enrolmentService.listStudentsOfEdition(7L, null, "t1")));
  }

  @Test
  @DisplayName("Search finds part of a name or the exact number")
  void editionSearch() {
    editionWithThreeStudents();
    assertEquals(List.of("Rui Test"), names(enrolmentService.listStudentsOfEdition(7L, "rui", null)));
    assertEquals(List.of("Eva Test"), names(enrolmentService.listStudentsOfEdition(7L, "50003", null)));
    assertTrue(enrolmentService.listStudentsOfEdition(7L, "nobody", null).isEmpty());
  }

  @Test
  @DisplayName("A student with an active statute shows no attendance (not applicable), the others do")
  void editionStatute() {
    // Arrange - 10 lessons, no absences, so without the statute it would be 100%
    editionWithThreeStudents();
    when(lessonService.countLessons(any())).thenReturn(10L);
    ana.assignStatute(StatuteType.WORKING_STUDENT, LocalDate.now().plusMonths(1));

    // Act
    EditionStudentDTO anaRow = enrolmentService.listStudentsOfEdition(7L, "ana", null).getFirst();
    EditionStudentDTO ruiRow = enrolmentService.listStudentsOfEdition(7L, "rui", null).getFirst();

    // Assert
    assertNull(anaRow.attendance());
    assertEquals("T2", anaRow.classSection());
    assertEquals(100.0, ruiRow.attendance());
  }

}
