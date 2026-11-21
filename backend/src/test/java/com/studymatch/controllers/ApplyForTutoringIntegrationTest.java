package com.studymatch.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import com.studymatch.models.Student;
import com.studymatch.models.Subject;
import com.studymatch.models.SubjectEdition;
import com.studymatch.models.Teacher;
import com.studymatch.models.TutorApplication;
import com.studymatch.repositories.AcademicYearRepository;
import com.studymatch.repositories.CategoryRepository;
import com.studymatch.repositories.DegreeRepository;
import com.studymatch.repositories.EnrolmentRepository;
import com.studymatch.repositories.StudentRepository;
import com.studymatch.repositories.SubjectEditionRepository;
import com.studymatch.repositories.SubjectRepository;
import com.studymatch.repositories.TeacherRepository;
import com.studymatch.repositories.TutorApplicationRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ApplyForTutoringIntegrationTest {

  private static final String PASSWORD = "Test1234";

  @Autowired
  private MockMvc mockMvc;
  @Autowired
  private PasswordEncoder passwordEncoder;
  @Autowired
  private DegreeRepository degreeRepository;
  @Autowired
  private StudentRepository studentRepository;
  @Autowired
  private TeacherRepository teacherRepository;
  @Autowired
  private CategoryRepository categoryRepository;
  @Autowired
  private SubjectRepository subjectRepository;
  @Autowired
  private AcademicYearRepository academicYearRepository;
  @Autowired
  private SubjectEditionRepository subjectEditionRepository;
  @Autowired
  private EnrolmentRepository enrolmentRepository;
  @Autowired
  private TutorApplicationRepository tutorApplicationRepository;

  private Teacher teacher;
  private Subject maths;
  private Student topStudent;
  private Student belowStudent;
  private MockHttpSession topSession;
  private MockHttpSession belowSession;

  @BeforeEach
  void setUp() throws Exception {
    teacher = teacherRepository.save(new Teacher("it.apply.teacher@example.com", "it.apply.teacher@studymatch.com",
        "Test Teacher", passwordEncoder.encode(PASSWORD)));
    Category category = categoryRepository.save(new Category("IT Apply Category"));
    maths = subjectRepository.save(new Subject("IT-APPLY-MATH", "Test Maths", 6, category));

    AcademicYear year = academicYearRepository.save(new AcademicYear(2091));
    SubjectEdition mathsEdition = new SubjectEdition(maths, year, Set.of(teacher));
    mathsEdition.addClassSection("T1");
    mathsEdition = subjectEditionRepository.save(mathsEdition);

    topStudent = saveStudent("it.apply.top@example.com", "Top Student");
    belowStudent = saveStudent("it.apply.below@example.com", "Below Student");

    // the top student passed with 18, the other with 15; only the top 5% (here the single best) is eligible
    Enrolment top = new Enrolment(topStudent, mathsEdition.getClassSection("T1"));
    top.setFinalGrade(18);
    enrolmentRepository.save(top);
    Enrolment below = new Enrolment(belowStudent, mathsEdition.getClassSection("T1"));
    below.setFinalGrade(15);
    enrolmentRepository.save(below);

    topSession = loginAndGetSession(topStudent.getInstitutionalEmail());
    belowSession = loginAndGetSession(belowStudent.getInstitutionalEmail());
  }

  private Student saveStudent(String personalEmail, String name) {
    Degree degree = degreeRepository.save(new Degree("IT Apply Degree " + name, 3));
    Student student = new Student(personalEmail, name, passwordEncoder.encode(PASSWORD), degree, 1);
    student.setMustChangePassword(false);
    student.setPersonalityProfile(new PersonalityProfile(0.5, 0.5, 0.5, 0.5, 0.5));
    student = studentRepository.save(student);
    student.generateInstitutionalEmail("studymatch.com");
    return studentRepository.saveAndFlush(student);
  }

  private MockHttpSession loginAndGetSession(String email) throws Exception {
    MvcResult result = mockMvc.perform(post("/users/login").with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
        .andReturn();
    assertEquals(200, result.getResponse().getStatus());
    return (MockHttpSession) result.getRequest().getSession(false);
  }

  private ResultActions apply(Long subjectId, MockHttpSession session) throws Exception {
    return mockMvc.perform(post("/tutor-applications/").with(csrf()).session(session)
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"subjectId\":" + subjectId + ",\"motivation\":\"test motivation\"}"));
  }

  @Test
  @DisplayName("A student in the top 5% applies and the application is Pending")
  void applies() throws Exception {
    apply(maths.getId(), topSession)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("PENDING"))
        .andExpect(jsonPath("$.subjectId").value(maths.getId()));
  }

  @Test
  @DisplayName("The passed subjects come back with their rank and eligibility")
  void listsApplicableSubjects() throws Exception {
    mockMvc.perform(get("/tutors/me/applicable-subjects").with(csrf()).session(topSession))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].subjectId").value(maths.getId()))
        .andExpect(jsonPath("$[0].eligible").value(true))
        .andExpect(jsonPath("$[0].position").value(1))
        .andExpect(jsonPath("$[0].hasCurrentEdition").value(true));
  }

  @Test
  @DisplayName("A student outside the top 5% can't apply")
  void notEligibleCannotApply() throws Exception {
    apply(maths.getId(), belowSession)
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("NOT_ELIGIBLE_FOR_TUTORING"));
  }

  @Test
  @DisplayName("A student can't apply twice while the first is Pending")
  void pendingCannotApply() throws Exception {
    apply(maths.getId(), topSession).andExpect(status().isCreated());
    apply(maths.getId(), topSession)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("TUTOR_APPLICATION_ALREADY_PENDING"));
  }

  @Test
  @DisplayName("A rejected application doesn't block a new one, and the list still works after reapplying")
  void rejectedDoesNotBlock() throws Exception {
    // Arrange - the student was rejected before
    TutorApplication rejected = new TutorApplication(topStudent, maths, "test motivation");
    rejected.reject(teacher, "not good enough");
    tutorApplicationRepository.saveAndFlush(rejected);

    // Act - a rejected application doesn't block a new one
    apply(maths.getId(), topSession).andExpect(status().isCreated());

    // the list still works with two applications (one rejected, one pending)
    mockMvc.perform(get("/tutors/me/applicable-subjects").with(csrf()).session(topSession))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].subjectId").value(maths.getId()))
        .andExpect(jsonPath("$[0].hasPendingApplication").value(true));
  }

  @Test
  @DisplayName("Teachers and unauthenticated users cannot apply")
  void onlyStudentsCanApply() throws Exception {
    teacher.setMustChangePassword(false);
    teacherRepository.saveAndFlush(teacher);

    mockMvc.perform(post("/tutor-applications/").with(csrf())
        .session(loginAndGetSession(teacher.getInstitutionalEmail()))
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"subjectId\":" + maths.getId() + "}"))
        .andExpect(status().isForbidden());
    mockMvc.perform(post("/tutor-applications/").with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"subjectId\":" + maths.getId() + "}"))
        .andExpect(status().isUnauthorized());
  }

}
