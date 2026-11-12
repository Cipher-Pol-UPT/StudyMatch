package com.studymatch.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;
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
import com.studymatch.repositories.StaffRepository;
import com.studymatch.repositories.StudentRepository;
import com.studymatch.repositories.SubjectEditionRepository;
import com.studymatch.repositories.SubjectRepository;
import com.studymatch.repositories.TeacherRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CreateAssignmentIntegrationTest {

  private static final String PASSWORD = "Test1234";
  private static final String TEACHER = "it.assign.teacher@studymatch.com";

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

  private SubjectEdition edition;
  private Student ana;
  private Student rui;
  private Student eva;

  // ana and rui are in the edition, eva isn't
  @BeforeEach
  void setUp() {
    staffRepository.save(withoutPasswordChange(new Staff("it.assign.staff@example.com",
        "it.assign.staff@studymatch.com", "Test Staff", passwordEncoder.encode(PASSWORD))));
    Teacher teacher = teacherRepository.save(withoutPasswordChange(new Teacher("it.assign.teacher@example.com",
        TEACHER, "Test Teacher", passwordEncoder.encode(PASSWORD))));
    teacherRepository.save(withoutPasswordChange(new Teacher("it.assign.other@example.com",
        "it.assign.other@studymatch.com", "Other Teacher", passwordEncoder.encode(PASSWORD))));

    Degree degree = degreeRepository.save(new Degree("IT Assign Degree", 3));
    Category category = categoryRepository.save(new Category("IT Assign Category"));
    Subject subject = subjectRepository.save(new Subject("ITASSIGN1", "IT Assign Subject", 6, category));
    AcademicYear year = academicYearRepository.save(new AcademicYear(2092));

    edition = new SubjectEdition(subject, year, Set.of(teacher));
    edition.addClassSection("T1");
    edition = subjectEditionRepository.save(edition);

    ana = studentRepository.save(new Student("it.assign.ana@example.com", "Ana Assign", "hash", degree, 1));
    rui = studentRepository.save(new Student("it.assign.rui@example.com", "Rui Assign", "hash", degree, 1));
    eva = studentRepository.save(new Student("it.assign.eva@example.com", "Eva Assign", "hash", degree, 1));
    enrolmentRepository.save(new Enrolment(ana, edition.getClassSections().iterator().next()));
    enrolmentRepository.save(new Enrolment(rui, edition.getClassSections().iterator().next()));
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

  private ResultActions create(String email, Long editionId, String body) throws Exception {
    return mockMvc.perform(post("/subject-editions/" + editionId + "/assignments").with(csrf())
        .session(loginAs(email))
        .contentType(MediaType.APPLICATION_JSON)
        .content(body));
  }

  private ResultActions create(String body) throws Exception {
    return create(TEACHER, edition.getId(), body);
  }

  // named after its type and weight, so assignments of one test don't clash on the name
  private static String individual(String type, int weight) {
    return """
        {"name":"%s %d","type":"%s","date":"2093-01-15","weight":%d}
        """.formatted(type, weight, type, weight);
  }

  private static String groupWork(String type, int minSize, int optimalSize, int maxSize, Long first, Long second) {
    return """
        {"name":"Project","type":"%s","date":"2093-01-15","weight":40,
         "grouping":{"minSize":%d,"optimalSize":%d,"maxSize":%d,"sameClass":true,
                     "separations":[{"firstStudent":%d,"secondStudent":%d}]}}
        """.formatted(type, minSize, optimalSize, maxSize, first, second);
  }

  private List<Assignment> savedAssignments() {
    return assignmentRepository.findBySubjectEdition(edition);
  }

  @Test
  @DisplayName("The edition's teacher creates a group project: 201 with its grouping, and it's saved")
  void teacherCreatesGroupProject() throws Exception {
    // Act
    create(groupWork("PROJECT", 2, 3, 4, ana.getNumber(), rui.getNumber()))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").isNumber())
        .andExpect(jsonPath("$.name").value("Project"))
        .andExpect(jsonPath("$.type").value("PROJECT"))
        .andExpect(jsonPath("$.date").value("2093-01-15"))
        .andExpect(jsonPath("$.weight").value(40))
        .andExpect(jsonPath("$.grouping.minSize").value(2))
        .andExpect(jsonPath("$.grouping.optimalSize").value(3))
        .andExpect(jsonPath("$.grouping.maxSize").value(4))
        .andExpect(jsonPath("$.grouping.sameClass").value(true))
        .andExpect(jsonPath("$.grouping.separations[0].firstStudent").value(ana.getNumber()))
        .andExpect(jsonPath("$.grouping.separations[0].secondStudent").value(rui.getNumber()));

    // Assert - stored as a fraction, with the separation
    Assignment saved = savedAssignments().getFirst();
    assertEquals(new BigDecimal("0.40"), saved.getWeight());
    assertEquals(1, saved.getGroupingRule().getSeparations().size());
  }

  @Test
  @DisplayName("An exam has no grouping")
  void teacherCreatesExam() throws Exception {
    create(individual("EXAM", 60))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.weight").value(60))
        .andExpect(jsonPath("$.grouping").doesNotExist());
  }

  @Test
  @DisplayName("70% used + 31% is 409 with the 30% left, and 70% + 30% fits exactly (boundary)")
  void weightsCannotGoOverFull() throws Exception {
    // Arrange
    create(individual("EXAM", 70)).andExpect(status().isCreated());

    // Act + Assert
    create(individual("PRACTICAL_EXAM", 31))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("ASSIGNMENT_WEIGHT_EXCEEDED"))
        .andExpect(jsonPath("$.params.remaining").value(30));
    create(individual("PRACTICAL_EXAM", 30)).andExpect(status().isCreated());
    assertEquals(2, savedAssignments().size());
  }

  @Test
  @DisplayName("Group work on an exam is 400 GROUP_WORK_NOT_ALLOWED_FOR_EXAMS")
  void examCannotBeGroupWork() throws Exception {
    create(groupWork("EXAM", 2, 3, 4, ana.getNumber(), rui.getNumber()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("GROUP_WORK_NOT_ALLOWED_FOR_EXAMS"));
    assertTrue(savedAssignments().isEmpty());
  }

  @Test
  @DisplayName("A minimum bigger than the optimal size is 400 INVALID_GROUP_SIZES")
  void inconsistentSizesAreRefused() throws Exception {
    create(groupWork("PROJECT", 3, 2, 4, ana.getNumber(), rui.getNumber()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_GROUP_SIZES"));
  }

  @Test
  @DisplayName("Separating a student who isn't in the edition is 400 STUDENT_NOT_ENROLLED with their number")
  void separationNeedsEnrolledStudents() throws Exception {
    create(groupWork("PROJECT", 2, 3, 4, ana.getNumber(), eva.getNumber()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("STUDENT_NOT_ENROLLED"))
        .andExpect(jsonPath("$.params.number").value(eva.getNumber()));
    assertTrue(savedAssignments().isEmpty());
  }

  @Test
  @DisplayName("A blank name and a 0% weight are field errors")
  void invalidFieldsAreFieldErrors() throws Exception {
    create("""
        {"name":" ","type":"EXAM","date":"2093-01-15","weight":0}
        """)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.fieldErrors[*].field", Matchers.containsInAnyOrder("name", "weight")));
  }

  @Test
  @DisplayName("A type that doesn't exist is a field error, not a crash")
  void unknownTypeIsFieldError() throws Exception {
    create(individual("ESSAY", 30))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors[0].field").value("type"))
        .andExpect(jsonPath("$.fieldErrors[0].code").value("INVALID"));
  }

  // who can create

  @Test
  @DisplayName("A teacher who doesn't teach the edition gets 403 ACCESS_DENIED")
  void otherTeacherDenied() throws Exception {
    create("it.assign.other@studymatch.com", edition.getId(), individual("EXAM", 30))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    assertTrue(savedAssignments().isEmpty());
  }

  @Test
  @DisplayName("Staff can't create assignments")
  void staffDenied() throws Exception {
    create("it.assign.staff@studymatch.com", edition.getId(), individual("EXAM", 30))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("An edition that doesn't exist is 404")
  void unknownEdition() throws Exception {
    create(TEACHER, 99999999L, individual("EXAM", 30)).andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("Without logging in it's 401")
  void notLoggedIn() throws Exception {
    mockMvc.perform(post("/subject-editions/" + edition.getId() + "/assignments").with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content(individual("EXAM", 30)))
        .andExpect(status().isUnauthorized());
  }

}
