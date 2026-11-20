package com.studymatch.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.studymatch.enums.AssignmentType;
import com.studymatch.models.Absence;
import com.studymatch.models.AcademicYear;
import com.studymatch.models.Assignment;
import com.studymatch.models.Category;
import com.studymatch.models.Degree;
import com.studymatch.models.Enrolment;
import com.studymatch.models.Grade;
import com.studymatch.models.Lesson;
import com.studymatch.models.Staff;
import com.studymatch.models.Student;
import com.studymatch.models.Subject;
import com.studymatch.models.SubjectEdition;
import com.studymatch.models.Teacher;
import com.studymatch.repositories.AbsenceRepository;
import com.studymatch.repositories.AcademicYearRepository;
import com.studymatch.repositories.AssignmentRepository;
import com.studymatch.repositories.CategoryRepository;
import com.studymatch.repositories.DegreeRepository;
import com.studymatch.repositories.EnrolmentRepository;
import com.studymatch.repositories.GradeRepository;
import com.studymatch.repositories.LessonRepository;
import com.studymatch.repositories.StaffRepository;
import com.studymatch.repositories.StudentRepository;
import com.studymatch.repositories.SubjectEditionRepository;
import com.studymatch.repositories.SubjectRepository;
import com.studymatch.repositories.TeacherRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ChangeStudentClassIntegrationTest {

  private static final String PASSWORD = "Test1234";
  private static final String STAFF = "it.class.staff@studymatch.com";
  private static final String TEACHER = "it.class.teacher@studymatch.com";

  @Autowired private MockMvc mockMvc;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private StaffRepository staffRepository;
  @Autowired private TeacherRepository teacherRepository;
  @Autowired private DegreeRepository degreeRepository;
  @Autowired private CategoryRepository categoryRepository;
  @Autowired private SubjectRepository subjectRepository;
  @Autowired private AcademicYearRepository academicYearRepository;
  @Autowired private SubjectEditionRepository subjectEditionRepository;
  @Autowired private StudentRepository studentRepository;
  @Autowired private EnrolmentRepository enrolmentRepository;
  @Autowired private AssignmentRepository assignmentRepository;
  @Autowired private GradeRepository gradeRepository;
  @Autowired private LessonRepository lessonRepository;
  @Autowired private AbsenceRepository absenceRepository;

  private SubjectEdition oldEdition;
  private SubjectEdition currentEdition;
  private Student ana;
  private Student rui;
  private Enrolment anaEnrolment;

  // ana is in T1 of the current edition (2096) with a grade and an absence; rui took the subject in 2095
  @BeforeEach
  void setUp() {
    staffRepository.save(withoutPasswordChange(new Staff("it.class.staff@example.com", STAFF, "Test Staff",
        passwordEncoder.encode(PASSWORD))));
    Teacher teacher = teacherRepository.save(withoutPasswordChange(new Teacher("it.class.teacher@example.com",
        TEACHER, "Test Teacher", passwordEncoder.encode(PASSWORD))));

    Degree degree = degreeRepository.save(new Degree("IT Class Degree", 3));
    Category category = categoryRepository.save(new Category("IT Class Category"));
    Subject subject = subjectRepository.save(new Subject("ITCLASS1", "IT Class Subject", 6, category));

    oldEdition = edition(subject, academicYearRepository.save(new AcademicYear(2095)), teacher, "T1", "T2");
    currentEdition = edition(subject, academicYearRepository.save(new AcademicYear(2096)), teacher, "T1", "T2");

    ana = studentRepository.save(new Student("it.class.ana@example.com", "Ana Class", "hash", degree, 1));
    rui = studentRepository.save(new Student("it.class.rui@example.com", "Rui Class", "hash", degree, 1));
    anaEnrolment = enrolmentRepository.save(new Enrolment(ana, currentEdition.getClassSection("T1")));
    enrolmentRepository.save(new Enrolment(rui, oldEdition.getClassSection("T1")));

    Assignment exam = assignmentRepository.save(new Assignment(currentEdition, "IT Class Exam", AssignmentType.EXAM,
        LocalDate.now(), new BigDecimal("0.50"), null));
    gradeRepository.save(new Grade(anaEnrolment, exam, 14.5));
    Lesson lesson = lessonRepository.save(new Lesson(currentEdition.getClassSection("T1"),
        LocalDateTime.now().minusDays(1).withNano(0).withSecond(0)));
    absenceRepository.save(new Absence(lesson, anaEnrolment, false));
  }

  private SubjectEdition edition(Subject subject, AcademicYear year, Teacher teacher, String... classes) {
    SubjectEdition edition = new SubjectEdition(subject, year, Set.of(teacher));
    for (String code : classes) {
      edition.addClassSection(code);
    }
    return subjectEditionRepository.save(edition);
  }

  private static <T extends com.studymatch.models.User> T withoutPasswordChange(T user) {
    user.setMustChangePassword(false);
    return user;
  }

  private MockHttpSession loginAs(String email) throws Exception {
    MvcResult result = mockMvc.perform(post("/users/login").with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
        .andReturn();
    assertEquals(200, result.getResponse().getStatus());
    return (MockHttpSession) result.getRequest().getSession(false);
  }

  private ResultActions changeClass(String email, Long editionId, Long studentNumber, String body) throws Exception {
    return mockMvc.perform(put("/subject-editions/" + editionId + "/enrolments/" + studentNumber + "/class-section")
        .with(csrf())
        .session(loginAs(email))
        .contentType(MediaType.APPLICATION_JSON)
        .content(body));
  }

  private ResultActions changeClass(Long editionId, Long studentNumber, String classSection) throws Exception {
    return changeClass(STAFF, editionId, studentNumber, "{\"classSection\":\"" + classSection + "\"}");
  }

  @Test
  @DisplayName("The student moves to the new class and keeps their grade and absence")
  void studentChangesClass() throws Exception {
    // Act
    changeClass(currentEdition.getId(), ana.getNumber(), "T2")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.subjectEditionId").value(currentEdition.getId()))
        .andExpect(jsonPath("$.subjectCode").value("ITCLASS1"))
        .andExpect(jsonPath("$.classSection").value("T2"))
        .andExpect(jsonPath("$.status").value("IN_PROGRESS"));

    // Assert
    Enrolment enrolment = enrolmentRepository.findById(anaEnrolment.getId()).orElseThrow();
    assertEquals("T2", enrolment.getClassSection().getCode());
    assertEquals(1, gradeRepository.findByEnrolment(enrolment).size());
    assertEquals(1, absenceRepository.findAll().stream()
        .filter(absence -> absence.getEnrolment().equals(enrolment)).count());
  }

  @Test
  @DisplayName("The student record shows the new class, with the edition id to change it again")
  void recordShowsTheNewClass() throws Exception {
    // Arrange
    changeClass(currentEdition.getId(), ana.getNumber(), "T2").andExpect(status().isOk());

    // Act + Assert
    mockMvc.perform(get("/students/" + ana.getNumber()).session(loginAs(STAFF)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.enrolments[0].subjectEditionId").value(currentEdition.getId()))
        .andExpect(jsonPath("$.enrolments[0].classSection").value("T2"));
  }

  @Test
  @DisplayName("Moving to the class the student is already in changes nothing")
  void sameClassIsFine() throws Exception {
    // Act + Assert
    changeClass(currentEdition.getId(), ana.getNumber(), "T1")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.classSection").value("T1"));
  }

  @Test
  @DisplayName("A class the edition doesn't have is 400 CLASS_SECTION_NOT_IN_SUBJECT_EDITION and the student stays")
  void unknownClassIsRefused() throws Exception {
    // Act
    changeClass(currentEdition.getId(), ana.getNumber(), "PL9")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("CLASS_SECTION_NOT_IN_SUBJECT_EDITION"));

    // Assert
    assertEquals("T1", enrolmentRepository.findById(anaEnrolment.getId()).orElseThrow().getClassSection().getCode());
  }

  @Test
  @DisplayName("A student who isn't in the edition is 400 STUDENT_NOT_ENROLLED with their number")
  void notEnrolledStudentIsRefused() throws Exception {
    // Act + Assert
    changeClass(currentEdition.getId(), rui.getNumber(), "T2")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("STUDENT_NOT_ENROLLED"))
        .andExpect(jsonPath("$.params.number").value(rui.getNumber()));
  }

  @Test
  @DisplayName("Last year's edition is 409 SUBJECT_EDITION_NOT_CURRENT")
  void oldEditionIsRefused() throws Exception {
    // Act + Assert
    changeClass(oldEdition.getId(), rui.getNumber(), "T2")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("SUBJECT_EDITION_NOT_CURRENT"));
  }

  @Test
  @DisplayName("An edition that doesn't exist is 404")
  void unknownEditionIsNotFound() throws Exception {
    // Act + Assert
    changeClass(-1L, ana.getNumber(), "T2")
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
  }

  @Test
  @DisplayName("A blank class is a NOT_BLANK field error")
  void blankClassIsFieldError() throws Exception {
    // Act + Assert
    changeClass(STAFF, currentEdition.getId(), ana.getNumber(), "{\"classSection\":\" \"}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.fieldErrors[0].field").value("classSection"))
        .andExpect(jsonPath("$.fieldErrors[0].code").value("NOT_BLANK"));
  }

  @Test
  @DisplayName("Teachers can't change a student's class")
  void teacherIsForbidden() throws Exception {
    // Act + Assert
    changeClass(TEACHER, currentEdition.getId(), ana.getNumber(), "{\"classSection\":\"T2\"}")
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("Without logging in it's 401")
  void anonymousIsUnauthorized() throws Exception {
    // Act + Assert
    mockMvc.perform(put("/subject-editions/" + currentEdition.getId() + "/enrolments/" + ana.getNumber()
        + "/class-section").with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"classSection\":\"T2\"}"))
        .andExpect(status().isUnauthorized());
  }

}
