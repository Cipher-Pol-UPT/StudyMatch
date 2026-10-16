package com.studymatch.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

import com.studymatch.enums.Language;
import com.studymatch.models.AcademicYear;
import com.studymatch.models.Category;
import com.studymatch.models.Degree;
import com.studymatch.models.Enrolment;
import com.studymatch.models.PersonalityProfile;
import com.studymatch.models.Student;
import com.studymatch.models.Subject;
import com.studymatch.models.SubjectEdition;
import com.studymatch.models.Teacher;
import com.studymatch.repositories.AcademicYearRepository;
import com.studymatch.repositories.CategoryRepository;
import com.studymatch.repositories.DegreeRepository;
import com.studymatch.repositories.EnrolmentRepository;
import com.studymatch.repositories.StudentRepository;
import com.studymatch.repositories.SubjectEditionRepository;
import com.studymatch.repositories.SubjectRepository;
import com.studymatch.repositories.TeacherRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class StudentProfileIntegrationTest {

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

  private Degree degree;
  private Student maria;
  private Student joao;
  private Teacher mariasTeacher;
  private Teacher otherTeacher;

  @BeforeEach
  void setUp() {
    degree = degreeRepository.save(new Degree("Integration Test Degree"));
    maria = saveStudent("maria.it", "Maria Test");
    joao = saveStudent("joao.it", "Joao Test");
    mariasTeacher = saveTeacher("teacher.it");
    otherTeacher = saveTeacher("other.teacher.it");
    enrol(maria, mariasTeacher);
  }

  @Test
  @DisplayName("A student gets their whole profile")
  void studentGetsTheirWholeProfile() throws Exception {
    mockMvc.perform(get("/students/me/profile").session(loginAs(maria.getInstitutionalEmail())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Maria Test"))
        .andExpect(jsonPath("$.languages[0]").value("PORTUGUESE"))
        .andExpect(jsonPath("$.privateProfile.institutionalEmail").value(maria.getInstitutionalEmail()))
        .andExpect(jsonPath("$.privateProfile.personalityProfile.leadership").value(0.5))
        .andExpect(jsonPath("$.privateProfile.hasAcademicHistory").value(true));
  }

  @Test
  @DisplayName("Another student gets only the public part")
  void anotherStudentGetsThePublicPart() throws Exception {
    mockMvc.perform(get("/students/" + maria.getNumber() + "/profile").session(loginAs(joao.getInstitutionalEmail())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Maria Test"))
        .andExpect(jsonPath("$.privateProfile").doesNotExist());
  }

  @Test
  @DisplayName("The student's teacher gets the whole profile")
  void theirTeacherGetsTheWholeProfile() throws Exception {
    mockMvc.perform(get("/students/" + maria.getNumber() + "/profile")
        .session(loginAs(mariasTeacher.getInstitutionalEmail())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.privateProfile.curricularYear").value(1));
  }

  @Test
  @DisplayName("A teacher who doesn't teach the student gets only the public part")
  void otherTeacherGetsThePublicPart() throws Exception {
    mockMvc.perform(get("/students/" + maria.getNumber() + "/profile")
        .session(loginAs(otherTeacher.getInstitutionalEmail())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.privateProfile").doesNotExist());
  }

  @Test
  @DisplayName("A teacher asking for their own student profile gets 403 ACCESS_DENIED")
  void teacherCannotUseMyProfile() throws Exception {
    mockMvc.perform(get("/students/me/profile").session(loginAs(mariasTeacher.getInstitutionalEmail())))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
  }

  @Test
  @DisplayName("An unknown student number gets 404 NOT_FOUND")
  void unknownStudentGetsNotFound() throws Exception {
    mockMvc.perform(get("/students/999999/profile").session(loginAs(maria.getInstitutionalEmail())))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
  }

  @Test
  @DisplayName("Without a session the profile gets 401 UNAUTHENTICATED")
  void noSessionGetsUnauthenticated() throws Exception {
    mockMvc.perform(get("/students/me/profile"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
  }

  @Test
  @DisplayName("Editing saves the changes")
  void editingSavesTheChanges() throws Exception {
    MockHttpSession session = loginAs(maria.getInstitutionalEmail());

    mockMvc.perform(put("/students/me/profile").session(session).with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content("""
            {"banner":"data:image/png;base64,iVBORw0KGgo=","aboutMe":"I like databases.",
             "skills":["Java"],"interests":["Chess"],"languages":["ENGLISH"]}
            """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.aboutMe").value("I like databases."));

    mockMvc.perform(get("/students/me/profile").session(session))
        .andExpect(jsonPath("$.banner").value("data:image/png;base64,iVBORw0KGgo="))
        .andExpect(jsonPath("$.skills[0]").value("Java"))
        .andExpect(jsonPath("$.interests[0]").value("Chess"))
        .andExpect(jsonPath("$.languages[0]").value("ENGLISH"));
  }

  @Test
  @DisplayName("Editing with no languages gets 400 AT_LEAST_ONE_LANGUAGE_REQUIRED")
  void editingWithoutLanguagesIsRejected() throws Exception {
    editMyProfile("""
        {"skills":[],"interests":[],"languages":[]}
        """)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("AT_LEAST_ONE_LANGUAGE_REQUIRED"));
  }

  @Test
  @DisplayName("Editing with a GIF banner gets 400 INVALID_BANNER")
  void editingWithAGifBannerIsRejected() throws Exception {
    editMyProfile("""
        {"banner":"data:image/gif;base64,R0lGODlh","skills":[],"interests":[],"languages":["ENGLISH"]}
        """)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_BANNER"));
  }

  @Test
  @DisplayName("Editing with an about me over 500 characters gets 400 VALIDATION_FAILED")
  void editingWithALongAboutMeIsRejected() throws Exception {
    editMyProfile("{\"aboutMe\":\"" + "a".repeat(501) + "\",\"skills\":[],\"interests\":[],\"languages\":[\"ENGLISH\"]}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.fieldErrors[0].field").value("aboutMe"))
        .andExpect(jsonPath("$.fieldErrors[0].code").value("SIZE_MAX"));
  }

  @Test
  @DisplayName("Editing without the CSRF token gets 403 CSRF_TOKEN_INVALID")
  void editingWithoutCsrfIsRejected() throws Exception {
    mockMvc.perform(put("/students/me/profile").session(loginAs(maria.getInstitutionalEmail()))
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"skills\":[],\"interests\":[],\"languages\":[\"ENGLISH\"]}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("CSRF_TOKEN_INVALID"));
  }

  private ResultActions editMyProfile(String body) throws Exception {
    return mockMvc.perform(put("/students/me/profile").session(loginAs(maria.getInstitutionalEmail())).with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content(body));
  }

  private MockHttpSession loginAs(String email) throws Exception {
    MvcResult result = mockMvc.perform(post("/users/login").with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
        .andReturn();
    assertEquals(200, result.getResponse().getStatus());
    return (MockHttpSession) result.getRequest().getSession(false);
  }

  private Student saveStudent(String emailName, String name) {
    Student student = new Student(emailName + "@example.com", name, passwordEncoder.encode(PASSWORD), degree, 1);
    student.setMustChangePassword(false);
    student.setPersonalityProfile(new PersonalityProfile(0.5, 0.5, 0.5, 0.5));
    student.setLanguages(Set.of(Language.PORTUGUESE));
    student = studentRepository.save(student);
    student.generateInstitutionalEmail("studymatch.com");
    return student;
  }

  private Teacher saveTeacher(String emailName) {
    Teacher teacher = new Teacher(emailName + "@example.com", emailName + "@studymatch.com", "Test Teacher",
        passwordEncoder.encode(PASSWORD));
    teacher.setMustChangePassword(false);
    return teacherRepository.save(teacher);
  }

  private void enrol(Student student, Teacher teacher) {
    Category category = categoryRepository.save(new Category("Integration Test Category"));
    Subject subject = subjectRepository.save(new Subject("IT-PROF", "Integration Test Subject", 6, category));
    AcademicYear academicYear = academicYearRepository.save(new AcademicYear(2099));
    SubjectEdition edition = new SubjectEdition(subject, academicYear, Set.of(teacher));
    edition.addClassSection("T1");
    edition = subjectEditionRepository.save(edition);
    enrolmentRepository.save(new Enrolment(student, edition.getClassSections().iterator().next()));
  }

}
