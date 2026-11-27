package com.studymatch.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.studymatch.models.AcademicYear;
import com.studymatch.models.Category;
import com.studymatch.models.Degree;
import com.studymatch.models.Enrolment;
import com.studymatch.models.PersonalityProfile;
import com.studymatch.models.Staff;
import com.studymatch.models.Student;
import com.studymatch.models.Subject;
import com.studymatch.models.SubjectEdition;
import com.studymatch.models.Teacher;
import com.studymatch.repositories.AcademicYearRepository;
import com.studymatch.repositories.CategoryRepository;
import com.studymatch.repositories.DegreeRepository;
import com.studymatch.repositories.EnrolmentRepository;
import com.studymatch.repositories.StaffRepository;
import com.studymatch.repositories.StudentRepository;
import com.studymatch.repositories.SubjectEditionRepository;
import com.studymatch.repositories.SubjectRepository;
import com.studymatch.repositories.TeacherRepository;

import jakarta.persistence.EntityManager;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class StartAcademicYearIntegrationTest {

  private static final String PASSWORD = "Test1234";
  private static final String STAFF = "it.year.staff@studymatch.com";
  private static final String TEACHER = "it.year.teacher@studymatch.com";
  // far in the future so it's newer than any real year in the test database
  private static final int CURRENT_YEAR = 2094;

  @Autowired private MockMvc mockMvc;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private EntityManager entityManager;
  @Autowired private StaffRepository staffRepository;
  @Autowired private TeacherRepository teacherRepository;
  @Autowired private DegreeRepository degreeRepository;
  @Autowired private CategoryRepository categoryRepository;
  @Autowired private SubjectRepository subjectRepository;
  @Autowired private AcademicYearRepository academicYearRepository;
  @Autowired private SubjectEditionRepository subjectEditionRepository;
  @Autowired private StudentRepository studentRepository;
  @Autowired private EnrolmentRepository enrolmentRepository;

  private MockHttpSession staff;
  private Student ana;
  private Student rui;

  // ana has no final grade yet, rui does; both took the personality test
  @BeforeEach
  void setUp() throws Exception {
    Staff member = new Staff("it.year.staff@example.com", STAFF, "Test Staff", passwordEncoder.encode(PASSWORD));
    member.setMustChangePassword(false);
    staffRepository.save(member);
    Teacher teacher = new Teacher("it.year.teacher@example.com", TEACHER, "Test Teacher",
        passwordEncoder.encode(PASSWORD));
    teacher.setMustChangePassword(false);
    teacherRepository.save(teacher);

    Degree degree = degreeRepository.save(new Degree("IT Year Degree", 3));
    Category category = categoryRepository.save(new Category("IT Year Category"));
    Subject subject = subjectRepository.save(new Subject("ITYEAR1", "IT Year Subject", 6, category));
    SubjectEdition edition = new SubjectEdition(subject, academicYearRepository.save(new AcademicYear(CURRENT_YEAR)),
        Set.of(teacher));
    edition.addClassSection("T1");
    edition = subjectEditionRepository.save(edition);

    ana = withProfile(new Student("it.year.ana@example.com", "Ana Year", "hash", degree, 1));
    rui = withProfile(new Student("it.year.rui@example.com", "Rui Year", "hash", degree, 1));
    enrolmentRepository.save(new Enrolment(ana, edition.getClassSection("T1")));
    Enrolment graded = new Enrolment(rui, edition.getClassSection("T1"));
    graded.setFinalGrade(15);
    enrolmentRepository.save(graded);

    staff = loginAs(STAFF);
  }

  private Student withProfile(Student student) {
    student.setPersonalityProfile(new PersonalityProfile(0.5, 0.5, 0.5, 0.5, 0.5));
    return studentRepository.save(student);
  }

  private MockHttpSession loginAs(String email) throws Exception {
    MvcResult result = mockMvc.perform(post("/users/login").with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
        .andReturn();
    assertEquals(200, result.getResponse().getStatus());
    return (MockHttpSession) result.getRequest().getSession(false);
  }

  // the year with semester 2 starting on s2Start, the rest are fine dates for that year
  private static String body(int year, String s2Start) {
    return "{\"startYear\":" + year + ",\"semesters\":{\"semester1Start\":\"" + year + "-09-15\",\"semester1End\":\""
        + (year + 1) + "-01-31\",\"semester2Start\":\"" + s2Start + "\",\"semester2End\":\"" + (year + 1) + "-06-30\"}}";
  }

  private static String body(int year) {
    return body(year, (year + 1) + "-02-15");
  }

  private ResultActions startYear(MockHttpSession session, String body) throws Exception {
    return mockMvc.perform(post("/academic-years/").with(csrf()).session(session)
        .contentType(MediaType.APPLICATION_JSON)
        .content(body));
  }

  // preview

  @Test
  @DisplayName("The preview shows the next year, the current one, and only the enrolments with no final grade")
  void previewListsUnfinishedEnrolments() throws Exception {
    mockMvc.perform(get("/academic-years/next").session(staff))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.nextStartYear").value(CURRENT_YEAR + 1))
        .andExpect(jsonPath("$.currentStartYear").value(CURRENT_YEAR))
        .andExpect(jsonPath("$.unfinishedEnrolments.length()").value(1))
        .andExpect(jsonPath("$.unfinishedEnrolments[0].studentNumber").value(ana.getNumber()))
        .andExpect(jsonPath("$.unfinishedEnrolments[0].subjectCode").value("ITYEAR1"))
        .andExpect(jsonPath("$.unfinishedEnrolments[0].classSection").value("T1"));
  }

  // starting the year

  @Test
  @DisplayName("Staff starts the next year: 201 with its dates, it becomes current and every personality profile is deleted")
  void staffStartsNextYear() throws Exception {
    // Act
    startYear(staff, body(CURRENT_YEAR + 1))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.startYear").value(CURRENT_YEAR + 1))
        .andExpect(jsonPath("$.semesters.semester1Start").value((CURRENT_YEAR + 1) + "-09-15"))
        .andExpect(jsonPath("$.semesters.semester2End").value((CURRENT_YEAR + 2) + "-06-30"));

    // Assert - flushed and reloaded, so the profile rows are really gone and not just unlinked
    entityManager.flush();
    entityManager.clear();
    mockMvc.perform(get("/academic-years/next").session(staff))
        .andExpect(jsonPath("$.currentStartYear").value(CURRENT_YEAR + 1));
    Student reloadedAna = studentRepository.findById(ana.getNumber()).orElseThrow();
    assertNull(reloadedAna.getPersonalityProfile());
    assertTrue(reloadedAna.requiresPersonalityTest());
    assertNull(studentRepository.findById(rui.getNumber()).orElseThrow().getPersonalityProfile());
    assertEquals(0L, entityManager.createQuery("select count(p) from PersonalityProfile p", Long.class)
        .getSingleResult());
  }

  @Test
  @DisplayName("Confirming the same year a second time gets 409 ACADEMIC_YEAR_NOT_NEXT")
  void secondConfirmationIsRefused() throws Exception {
    // Arrange
    startYear(staff, body(CURRENT_YEAR + 1)).andExpect(status().isCreated());

    // Act + Assert
    startYear(staff, body(CURRENT_YEAR + 1))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("ACADEMIC_YEAR_NOT_NEXT"));
  }

  @Test
  @DisplayName("A year that isn't the next one gets 409 ACADEMIC_YEAR_NOT_NEXT and nothing changes")
  void wrongYearIsRefused() throws Exception {
    // Act
    startYear(staff, body(CURRENT_YEAR + 2))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("ACADEMIC_YEAR_NOT_NEXT"));

    // Assert
    assertTrue(academicYearRepository.findById(CURRENT_YEAR + 2).isEmpty());
    assertEquals(0.5, studentRepository.findById(ana.getNumber()).orElseThrow().getPersonalityProfile().getOpenness());
  }

  @Test
  @DisplayName("Saving a year that already exists fails on the primary key instead of merging into it")
  void existingYearIsNotMerged() {
    // Arrange - cleared so it's the database refusing it, like a confirmation from another request
    entityManager.flush();
    entityManager.clear();

    // Act + Assert
    assertThrows(DataIntegrityViolationException.class,
        () -> academicYearRepository.saveAndFlush(new AcademicYear(CURRENT_YEAR)));
  }

  @Test
  @DisplayName("Without the year to confirm it's 400")
  void missingYearIsRefused() throws Exception {
    startYear(staff, "{}")
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("Without semester dates it's a field error on semesters and no year starts")
  void missingSemestersAreRefused() throws Exception {
    // Act
    startYear(staff, "{\"startYear\":" + (CURRENT_YEAR + 1) + "}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.fieldErrors[0].field").value("semesters"));

    // Assert
    assertTrue(academicYearRepository.findById(CURRENT_YEAR + 1).isEmpty());
  }

  @Test
  @DisplayName("Semester 2 starting the day semester 1 ends is 400 SEMESTERS_OVERLAP, no year starts and no profile is wiped")
  void overlappingSemestersAreRefused() throws Exception {
    // Act
    startYear(staff, body(CURRENT_YEAR + 1, (CURRENT_YEAR + 2) + "-01-31"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("SEMESTERS_OVERLAP"));

    // Assert
    assertTrue(academicYearRepository.findById(CURRENT_YEAR + 1).isEmpty());
    assertEquals(0.5, studentRepository.findById(ana.getNumber()).orElseThrow().getPersonalityProfile().getOpenness());
  }

  // who can do it

  @Test
  @DisplayName("A teacher can't see the preview or start a year (403 ACCESS_DENIED)")
  void teacherIsForbidden() throws Exception {
    // Arrange
    MockHttpSession teacher = loginAs(TEACHER);

    // Act + Assert
    mockMvc.perform(get("/academic-years/next").session(teacher))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    startYear(teacher, body(CURRENT_YEAR + 1))
        .andExpect(status().isForbidden());
  }

}
