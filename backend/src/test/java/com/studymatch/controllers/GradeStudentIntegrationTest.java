package com.studymatch.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

import org.hamcrest.Matchers;
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
import com.studymatch.enums.EnrolmentStatus;
import com.studymatch.models.AcademicYear;
import com.studymatch.models.Assignment;
import com.studymatch.models.Category;
import com.studymatch.models.Degree;
import com.studymatch.models.Enrolment;
import com.studymatch.models.Staff;
import com.studymatch.models.Student;
import com.studymatch.models.Subject;
import com.studymatch.models.SubjectEdition;
import com.studymatch.models.Teacher;
import com.studymatch.repositories.AcademicYearRepository;
import com.studymatch.repositories.AssignmentRepository;
import com.studymatch.repositories.CategoryRepository;
import com.studymatch.repositories.DegreeRepository;
import com.studymatch.repositories.EnrolmentRepository;
import com.studymatch.repositories.GradeRepository;
import com.studymatch.repositories.StaffRepository;
import com.studymatch.repositories.StudentRepository;
import com.studymatch.repositories.SubjectEditionRepository;
import com.studymatch.repositories.SubjectRepository;
import com.studymatch.repositories.TeacherRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class GradeStudentIntegrationTest {

  private static final String PASSWORD = "Test1234";
  private static final String TEACHER = "it.grade.teacher@studymatch.com";
  private static final String OTHER_TEACHER = "it.grade.other@studymatch.com";
  private static final String STAFF = "it.grade.staff@studymatch.com";

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

  private SubjectEdition edition;
  private Assignment project;
  private Assignment exam;
  private Assignment otherEditionsExam;
  private Student ana;
  private Student rui;
  private Student eva;

  // project 40% + exam 60%; ana and rui are in the edition, eva isn't; the teacher also has last year's edition
  @BeforeEach
  void setUp() {
    staffRepository.save(withoutPasswordChange(new Staff("it.grade.staff@example.com", STAFF, "Test Staff",
        passwordEncoder.encode(PASSWORD))));
    Teacher teacher = teacherRepository.save(withoutPasswordChange(new Teacher("it.grade.teacher@example.com",
        TEACHER, "Test Teacher", passwordEncoder.encode(PASSWORD))));
    teacherRepository.save(withoutPasswordChange(new Teacher("it.grade.other@example.com", OTHER_TEACHER,
        "Other Teacher", passwordEncoder.encode(PASSWORD))));

    Degree degree = degreeRepository.save(new Degree("IT Grade Degree", 3));
    Category category = categoryRepository.save(new Category("IT Grade Category"));
    Subject subject = subjectRepository.save(new Subject("ITGRADE1", "IT Grade Subject", 6, category));

    edition = edition(subject, 2090, teacher);
    SubjectEdition lastYear = edition(subject, 2089, teacher);

    project = assignment(edition, "Project", AssignmentType.PROJECT, LocalDate.of(2090, 12, 1), "0.40");
    exam = assignment(edition, "Exam", AssignmentType.EXAM, LocalDate.of(2091, 1, 15), "0.60");
    otherEditionsExam = assignment(lastYear, "Exam", AssignmentType.EXAM, LocalDate.of(2090, 1, 15), "1.00");

    ana = studentRepository.save(new Student("it.grade.ana@example.com", "Ana Grade", "hash", degree, 1));
    rui = studentRepository.save(new Student("it.grade.rui@example.com", "Rui Grade", "hash", degree, 1));
    eva = studentRepository.save(new Student("it.grade.eva@example.com", "Eva Grade", "hash", degree, 1));
    enrolmentRepository.save(new Enrolment(ana, edition.getClassSections().iterator().next()));
    enrolmentRepository.save(new Enrolment(rui, edition.getClassSections().iterator().next()));
  }

  private SubjectEdition edition(Subject subject, int startYear, Teacher teacher) {
    SubjectEdition edition = new SubjectEdition(subject, academicYearRepository.save(new AcademicYear(startYear)),
        Set.of(teacher));
    edition.addClassSection("T1");
    return subjectEditionRepository.save(edition);
  }

  private Assignment assignment(SubjectEdition edition, String name, AssignmentType type, LocalDate date,
      String weight) {
    return assignmentRepository.save(new Assignment(edition, name, type, date, new BigDecimal(weight), null));
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

  private ResultActions grade(String email, Assignment assignment, Student student, String value) throws Exception {
    return mockMvc.perform(put("/subject-editions/" + edition.getId() + "/assignments/" + assignment.getId()
        + "/grades/" + student.getNumber()).with(csrf())
        .session(loginAs(email))
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"value\":" + value + "}"));
  }

  private ResultActions grade(Assignment assignment, Student student, String value) throws Exception {
    return grade(TEACHER, assignment, student, value);
  }

  private ResultActions sheet(String email) throws Exception {
    return mockMvc.perform(get("/subject-editions/" + edition.getId() + "/grades").session(loginAs(email)));
  }

  private Enrolment enrolmentOf(Student student) {
    return enrolmentRepository.findBySubjectEditionIdAndStudent_Number(edition.getId(), student.getNumber())
        .orElseThrow();
  }

  // grade sheet

  @Test
  @DisplayName("The sheet lists the assignments by date and the students by name, with their grades")
  void gradeSheet() throws Exception {
    // Arrange
    grade(project, rui, "14.5").andExpect(status().isOk());

    // Act + Assert
    sheet(TEACHER)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.assignments[0].name").value("Project"))
        .andExpect(jsonPath("$.assignments[0].weight").value(40))
        .andExpect(jsonPath("$.assignments[1].name").value("Exam"))
        .andExpect(jsonPath("$.students.length()").value(2))
        .andExpect(jsonPath("$.students[0].name").value("Ana Grade"))
        .andExpect(jsonPath("$.students[0].classSection").value("T1"))
        .andExpect(jsonPath("$.students[1].name").value("Rui Grade"))
        .andExpect(jsonPath("$.students[1].grades['" + project.getId() + "']").value(14.5))
        .andExpect(jsonPath("$.students[1].status").value("IN_PROGRESS"))
        .andExpect(jsonPath("$.weightsComplete").value(true));
  }

  // grade student

  @Test
  @DisplayName("A first grade is saved, no final grade while the exam is missing")
  void firstGrade() throws Exception {
    // Act + Assert
    grade(project, ana, "14")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.student.number").value(ana.getNumber()))
        .andExpect(jsonPath("$.student.grades['" + project.getId() + "']").value(14.0))
        .andExpect(jsonPath("$.student.finalGrade").value(Matchers.nullValue()))
        .andExpect(jsonPath("$.weightsComplete").value(true));
    assertEquals(1, gradeRepository.findByEnrolment(enrolmentOf(ana)).size());
  }

  @Test
  @DisplayName("The last grade sets the weighted final grade and the enrolment status")
  void finalGrade() throws Exception {
    // Arrange
    grade(project, ana, "14").andExpect(status().isOk());

    // Act + Assert - 14 x 40% + 12 x 60% = 12.8
    grade(exam, ana, "12")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.student.finalGrade").value(13))
        .andExpect(jsonPath("$.student.status").value("PASSED"));
    assertEquals(13, enrolmentOf(ana).getFinalGrade());
    assertEquals(EnrolmentStatus.PASSED, enrolmentOf(ana).getStatus());
  }

  @Test
  @DisplayName("Grading again replaces the grade and recalculates the final grade")
  void regrade() throws Exception {
    // Arrange
    grade(project, ana, "14").andExpect(status().isOk());
    grade(exam, ana, "12").andExpect(status().isOk());

    // Act + Assert - 14 x 40% + 2 x 60% = 6.8
    grade(exam, ana, "2")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.student.finalGrade").value(7))
        .andExpect(jsonPath("$.student.status").value("FAILED"));
    assertEquals(2, gradeRepository.findByEnrolment(enrolmentOf(ana)).size());
  }

  @Test
  @DisplayName("Weights under 100%: the grade is saved, there's no final grade and weightsComplete is false")
  void incompleteWeights() throws Exception {
    // Arrange - only the project (40%) is left
    assignmentRepository.delete(exam);

    // Act + Assert
    grade(project, ana, "14")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.student.finalGrade").value(Matchers.nullValue()))
        .andExpect(jsonPath("$.weightsComplete").value(false));
    assertEquals(EnrolmentStatus.IN_PROGRESS, enrolmentOf(ana).getStatus());
  }

  @Test
  @DisplayName("A grade over 20 is a DECIMAL_MAX field error and nothing is saved")
  void gradeOverTwenty() throws Exception {
    // Act + Assert
    grade(project, ana, "20.01")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors[0].field").value("value"))
        .andExpect(jsonPath("$.fieldErrors[0].code").value("DECIMAL_MAX"));
    assertTrue(gradeRepository.findByEnrolment(enrolmentOf(ana)).isEmpty());
  }

  @Test
  @DisplayName("A student who isn't in the edition is 400 STUDENT_NOT_ENROLLED")
  void studentNotEnrolled() throws Exception {
    grade(project, eva, "14")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("STUDENT_NOT_ENROLLED"))
        .andExpect(jsonPath("$.params.number").value(eva.getNumber()));
  }

  @Test
  @DisplayName("An assignment of another edition is 404, even one the same teacher has")
  void otherEditionsAssignment() throws Exception {
    grade(otherEditionsExam, ana, "14")
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    assertTrue(gradeRepository.findByEnrolment(enrolmentOf(ana)).isEmpty());
  }

  // who can grade

  @Test
  @DisplayName("A teacher who doesn't teach the edition gets 403 ACCESS_DENIED on both")
  void otherTeacherDenied() throws Exception {
    grade(OTHER_TEACHER, project, ana, "14")
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    sheet(OTHER_TEACHER)
        .andExpect(status().isForbidden());
    assertTrue(gradeRepository.findByEnrolment(enrolmentOf(ana)).isEmpty());
  }

  @Test
  @DisplayName("Staff can't grade or see the sheet")
  void staffDenied() throws Exception {
    grade(STAFF, project, ana, "14")
        .andExpect(status().isForbidden());
    sheet(STAFF)
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("Without logging in it's 401")
  void notLoggedIn() throws Exception {
    mockMvc.perform(get("/subject-editions/" + edition.getId() + "/grades"))
        .andExpect(status().isUnauthorized());
  }

}
