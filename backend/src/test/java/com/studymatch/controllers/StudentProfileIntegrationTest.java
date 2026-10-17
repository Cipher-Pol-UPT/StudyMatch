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
import com.studymatch.models.Admin;
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
import com.studymatch.repositories.AdminRepository;
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
class StudentProfileIntegrationTest {

  private static final String PASSWORD = "Test1234";
  private static final String PNG_BANNER = "data:image/png;base64,iVBORw0KGgo=";

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
  private StaffRepository staffRepository;

  @Autowired
  private AdminRepository adminRepository;

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
  private Student student;
  private Student otherStudent;
  private Teacher theirTeacher;
  private Teacher otherTeacher;

  @BeforeEach
  void setUp() {
    degree = degreeRepository.save(new Degree("Integration Test Degree"));
    student = saveStudent("student.it", "Test Student");
    otherStudent = saveStudent("other.student.it", "Other Test Student");
    theirTeacher = saveTeacher("teacher.it");
    otherTeacher = saveTeacher("other.teacher.it");
    enrol(student, theirTeacher);
  }

  @Test
  @DisplayName("A student gets their whole profile")
  void studentGetsTheirWholeProfile() throws Exception {
    // Arrange
    MockHttpSession session = loginAs(student.getInstitutionalEmail());

    // Act + Assert
    mockMvc.perform(get("/students/me/profile").session(session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Test Student"))
        .andExpect(jsonPath("$.languages[0]").value("PORTUGUESE"))
        .andExpect(jsonPath("$.privateProfile.institutionalEmail").value(student.getInstitutionalEmail()))
        .andExpect(jsonPath("$.privateProfile.personalityProfile.leadership").value(0.5))
        .andExpect(jsonPath("$.privateProfile.hasAcademicHistory").value(true));
  }

  @Test
  @DisplayName("Another student gets only the public part")
  void anotherStudentGetsThePublicPart() throws Exception {
    // Arrange
    MockHttpSession session = loginAs(otherStudent.getInstitutionalEmail());

    // Act + Assert
    mockMvc.perform(get(profileOf(student)).session(session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Test Student"))
        .andExpect(jsonPath("$.privateProfile").doesNotExist());
  }

  @Test
  @DisplayName("The student's teacher gets the whole profile")
  void theirTeacherGetsTheWholeProfile() throws Exception {
    // Arrange
    MockHttpSession session = loginAs(theirTeacher.getInstitutionalEmail());

    // Act + Assert
    mockMvc.perform(get(profileOf(student)).session(session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.privateProfile.curricularYear").value(1));
  }

  @Test
  @DisplayName("A teacher who doesn't teach the student gets only the public part")
  void otherTeacherGetsThePublicPart() throws Exception {
    // Arrange
    MockHttpSession session = loginAs(otherTeacher.getInstitutionalEmail());

    // Act + Assert
    mockMvc.perform(get(profileOf(student)).session(session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.privateProfile").doesNotExist());
  }

  @Test
  @DisplayName("Staff get the whole profile")
  void staffGetTheWholeProfile() throws Exception {
    // Arrange
    Staff staff = new Staff("staff.it@example.com", "staff.it@studymatch.com", "Test Staff",
        passwordEncoder.encode(PASSWORD));
    staff.setMustChangePassword(false);
    staffRepository.save(staff);
    MockHttpSession session = loginAs(staff.getInstitutionalEmail());

    // Act + Assert
    mockMvc.perform(get(profileOf(student)).session(session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.privateProfile.institutionalEmail").value(student.getInstitutionalEmail()));
  }

  @Test
  @DisplayName("An admin gets only the public part")
  void adminGetsThePublicPart() throws Exception {
    // Arrange
    Admin admin = new Admin("admin.it@example.com", "admin.it@studymatch.com", "Test Admin",
        passwordEncoder.encode(PASSWORD));
    admin.setMustChangePassword(false);
    adminRepository.save(admin);
    MockHttpSession session = loginAs(admin.getInstitutionalEmail());

    // Act + Assert
    mockMvc.perform(get(profileOf(student)).session(session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Test Student"))
        .andExpect(jsonPath("$.privateProfile").doesNotExist());
  }

  @Test
  @DisplayName("A teacher asking for their own student profile gets 403 ACCESS_DENIED")
  void teacherCannotUseMyProfile() throws Exception {
    // Arrange
    MockHttpSession session = loginAs(theirTeacher.getInstitutionalEmail());

    // Act + Assert
    mockMvc.perform(get("/students/me/profile").session(session))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
  }

  @Test
  @DisplayName("An unknown student number gets 404 NOT_FOUND")
  void unknownStudentGetsNotFound() throws Exception {
    // Arrange
    MockHttpSession session = loginAs(student.getInstitutionalEmail());

    // Act + Assert
    mockMvc.perform(get("/students/999999/profile").session(session))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
  }

  @Test
  @DisplayName("Without a session the profile gets 401 UNAUTHENTICATED")
  void noSessionGetsUnauthenticated() throws Exception {
    // Act + Assert
    mockMvc.perform(get("/students/me/profile"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
  }

  @Test
  @DisplayName("Editing saves the changes")
  void editingSavesTheChanges() throws Exception {
    // Arrange
    MockHttpSession session = loginAs(student.getInstitutionalEmail());

    // Act
    mockMvc.perform(put("/students/me/profile").session(session).with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content("""
            {"banner":"%s","aboutMe":"Test about me",
             "skills":["Test Skill"],"interests":["Test Interest"],"languages":["ENGLISH"]}
            """.formatted(PNG_BANNER)))
        .andExpect(status().isOk());

    // Assert
    mockMvc.perform(get("/students/me/profile").session(session))
        .andExpect(jsonPath("$.banner").value(PNG_BANNER))
        .andExpect(jsonPath("$.aboutMe").value("Test about me"))
        .andExpect(jsonPath("$.skills[0]").value("Test Skill"))
        .andExpect(jsonPath("$.interests[0]").value("Test Interest"))
        .andExpect(jsonPath("$.languages[0]").value("ENGLISH"));
  }

  @Test
  @DisplayName("Editing with no languages gets 400 AT_LEAST_ONE_LANGUAGE_REQUIRED")
  void editingWithoutLanguagesIsRejected() throws Exception {
    // Act + Assert
    editMyProfile("""
        {"skills":[],"interests":[],"languages":[]}
        """)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("AT_LEAST_ONE_LANGUAGE_REQUIRED"));
  }

  @Test
  @DisplayName("Editing with a GIF banner gets 400 INVALID_BANNER")
  void editingWithAGifBannerIsRejected() throws Exception {
    // Act + Assert
    editMyProfile("""
        {"banner":"data:image/gif;base64,R0lGODlh","skills":[],"interests":[],"languages":["ENGLISH"]}
        """)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_BANNER"));
  }

  @Test
  @DisplayName("Editing with an about me over 500 characters gets 400 VALIDATION_FAILED")
  void editingWithALongAboutMeIsRejected() throws Exception {
    // Act + Assert
    editMyProfile("{\"aboutMe\":\"" + "a".repeat(501) + "\",\"skills\":[],\"interests\":[],\"languages\":[\"ENGLISH\"]}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.fieldErrors[0].field").value("aboutMe"))
        .andExpect(jsonPath("$.fieldErrors[0].code").value("SIZE_MAX"));
  }

  @Test
  @DisplayName("Editing without the CSRF token gets 403 CSRF_TOKEN_INVALID")
  void editingWithoutCsrfIsRejected() throws Exception {
    // Arrange
    MockHttpSession session = loginAs(student.getInstitutionalEmail());

    // Act + Assert
    mockMvc.perform(put("/students/me/profile").session(session)
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"skills\":[],\"interests\":[],\"languages\":[\"ENGLISH\"]}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("CSRF_TOKEN_INVALID"));
  }

  private ResultActions editMyProfile(String body) throws Exception {
    return mockMvc.perform(put("/students/me/profile").session(loginAs(student.getInstitutionalEmail())).with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content(body));
  }

  private static String profileOf(Student student) {
    return "/students/" + student.getNumber() + "/profile";
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
    Student newStudent = new Student(emailName + "@example.com", name, passwordEncoder.encode(PASSWORD), degree, 1);
    newStudent.setMustChangePassword(false);
    newStudent.setPersonalityProfile(new PersonalityProfile(0.5, 0.5, 0.5, 0.5));
    newStudent.setLanguages(Set.of(Language.PORTUGUESE));
    newStudent = studentRepository.save(newStudent);
    newStudent.generateInstitutionalEmail("studymatch.com");
    return newStudent;
  }

  private Teacher saveTeacher(String emailName) {
    Teacher teacher = new Teacher(emailName + "@example.com", emailName + "@studymatch.com", "Test Teacher",
        passwordEncoder.encode(PASSWORD));
    teacher.setMustChangePassword(false);
    return teacherRepository.save(teacher);
  }

  private void enrol(Student enrolledStudent, Teacher teacher) {
    Category category = categoryRepository.save(new Category("Integration Test Category"));
    Subject subject = subjectRepository.save(new Subject("IT-PROFILE", "Test Subject", 6, category));
    AcademicYear academicYear = academicYearRepository.save(new AcademicYear(2099));
    SubjectEdition edition = new SubjectEdition(subject, academicYear, Set.of(teacher));
    edition.addClassSection("T1");
    edition = subjectEditionRepository.save(edition);
    enrolmentRepository.save(new Enrolment(enrolledStudent, edition.getClassSections().iterator().next()));
  }

}
