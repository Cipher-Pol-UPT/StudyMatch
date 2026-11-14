package com.studymatch.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.springframework.transaction.annotation.Transactional;

import com.studymatch.models.Category;
import com.studymatch.models.Degree;
import com.studymatch.models.PersonalityProfile;
import com.studymatch.models.Student;
import com.studymatch.models.Subject;
import com.studymatch.models.Teacher;
import com.studymatch.models.TutorApplication;
import com.studymatch.repositories.CategoryRepository;
import com.studymatch.repositories.DegreeRepository;
import com.studymatch.repositories.StudentRepository;
import com.studymatch.repositories.SubjectRepository;
import com.studymatch.repositories.TeacherRepository;
import com.studymatch.repositories.TutorApplicationRepository;
import com.studymatch.repositories.TutorRoleRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TutorSubjectsIntegrationTest {

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
  private TutorApplicationRepository tutorApplicationRepository;

  @Autowired
  private TutorRoleRepository tutorRoleRepository;

  private Teacher teacher;
  private Subject maths;
  private Subject physics;
  private Student tutor;

  // a tutor of two subjects, saved physics first so the sorting is actually tested
  @BeforeEach
  void setUp() {
    teacher = new Teacher("it.ts.teacher@example.com", "it.ts.teacher@studymatch.com", "Test Teacher",
        passwordEncoder.encode(PASSWORD));
    teacher.setMustChangePassword(false);
    teacher = teacherRepository.save(teacher);
    Category category = categoryRepository.save(new Category("IT Tutor Subjects Category"));
    physics = subjectRepository.save(new Subject("IT-TS-PHYS", "Test Physics", 6, category));
    maths = subjectRepository.save(new Subject("IT-TS-MATH", "Test Maths", 6, category));
    tutor = saveStudent("it.ts.tutor@example.com", "Tutor Test");
    saveTutorRole(tutor, physics);
    saveTutorRole(tutor, maths);
  }

  private Student saveStudent(String personalEmail, String name) {
    Degree degree = degreeRepository.save(new Degree("IT Tutor Subjects Degree " + name, 3));
    Student student = new Student(personalEmail, name, passwordEncoder.encode(PASSWORD), degree, 1);
    student.setMustChangePassword(false);
    student.setPersonalityProfile(new PersonalityProfile(0.5, 0.5, 0.5, 0.5));
    student = studentRepository.save(student);
    student.generateInstitutionalEmail("studymatch.com");
    return studentRepository.saveAndFlush(student);
  }

  private void saveTutorRole(Student student, Subject subject) {
    TutorApplication application = tutorApplicationRepository
        .save(new TutorApplication(student, subject, "test motivation"));
    tutorRoleRepository.save(application.approve(teacher));
  }

  private MockHttpSession loginAs(String email) throws Exception {
    MvcResult result = mockMvc.perform(post("/users/login").with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
        .andReturn();
    assertEquals(200, result.getResponse().getStatus());
    return (MockHttpSession) result.getRequest().getSession(false);
  }

  @Test
  @DisplayName("A tutor gets the subjects they tutor with their ids, sorted by name")
  void tutorGetsSubjects() throws Exception {
    mockMvc.perform(get("/tutors/me/subjects").session(loginAs(tutor.getInstitutionalEmail())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[0].subjectId").value(maths.getId()))
        .andExpect(jsonPath("$[0].subjectName").value("Test Maths"))
        .andExpect(jsonPath("$[1].subjectId").value(physics.getId()))
        .andExpect(jsonPath("$[1].subjectName").value("Test Physics"));
  }

  @Test
  @DisplayName("Another tutor's subjects aren't in the list")
  void onlyOwnSubjects() throws Exception {
    // Arrange
    Student other = saveStudent("it.ts.other@example.com", "Other Test");
    saveTutorRole(other, physics);

    // Act + Assert
    mockMvc.perform(get("/tutors/me/subjects").session(loginAs(other.getInstitutionalEmail())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].subjectName").value("Test Physics"));
  }

  @Test
  @DisplayName("A student who doesn't tutor anything gets an empty list")
  void notATutor() throws Exception {
    // Arrange
    Student student = saveStudent("it.ts.student@example.com", "Student Test");

    // Act + Assert
    mockMvc.perform(get("/tutors/me/subjects").session(loginAs(student.getInstitutionalEmail())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
  }

  @Test
  @DisplayName("Teachers can't use it (403 ACCESS_DENIED)")
  void onlyStudents() throws Exception {
    mockMvc.perform(get("/tutors/me/subjects").session(loginAs("it.ts.teacher@studymatch.com")))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
  }

  @Test
  @DisplayName("Not logged in is 401 UNAUTHENTICATED")
  void notLoggedIn() throws Exception {
    mockMvc.perform(get("/tutors/me/subjects"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
  }

}
