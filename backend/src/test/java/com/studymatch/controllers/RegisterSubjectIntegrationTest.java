package com.studymatch.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
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
import com.studymatch.models.Admin;
import com.studymatch.models.Category;
import com.studymatch.models.Degree;
import com.studymatch.models.Staff;
import com.studymatch.models.Subject;
import com.studymatch.models.SubjectEdition;
import com.studymatch.models.Teacher;
import com.studymatch.repositories.AcademicYearRepository;
import com.studymatch.repositories.AdminRepository;
import com.studymatch.repositories.CategoryRepository;
import com.studymatch.repositories.DegreeRepository;
import com.studymatch.repositories.StaffRepository;
import com.studymatch.repositories.SubjectEditionRepository;
import com.studymatch.repositories.SubjectRepository;
import com.studymatch.repositories.TeacherRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RegisterSubjectIntegrationTest {

  private static final String PASSWORD = "Test1234";
  private static final String CODE = "ITSUBJ1";
  // far in the future so it can't clash with real academic years in the database
  private static final int YEAR = 2091;
  private static final int OTHER_YEAR = 2092;

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private StaffRepository staffRepository;

  @Autowired
  private TeacherRepository teacherRepository;

  @Autowired
  private AdminRepository adminRepository;

  @Autowired
  private CategoryRepository categoryRepository;

  @Autowired
  private DegreeRepository degreeRepository;

  @Autowired
  private AcademicYearRepository academicYearRepository;

  @Autowired
  private SubjectRepository subjectRepository;

  @Autowired
  private SubjectEditionRepository subjectEditionRepository;

  @Autowired
  private PasswordEncoder passwordEncoder;

  private Category category;
  private Degree degree;
  private AcademicYear year;
  private Teacher teacher;

  @BeforeEach
  void saveFormOptions() {
    category = categoryRepository.save(new Category("IT Subject Category"));
    degree = degreeRepository.save(new Degree("IT Subject Degree", 3));
    year = academicYearRepository.save(new AcademicYear(YEAR));
    academicYearRepository.save(new AcademicYear(OTHER_YEAR));
    teacher = teacherRepository.save(new Teacher("it.subject.teacher@example.com", "it.subject.teacher@studymatch.com",
        "IT Subject Teacher", passwordEncoder.encode(PASSWORD)));
  }

  private String newSubject(String code, long categoryId, String degrees, String edition) {
    return "{\"code\":\"" + code + "\",\"name\":\"IT Test Subject\",\"ects\":6,\"categoryId\":" + categoryId
        + ",\"degrees\":" + degrees + ",\"edition\":" + edition + "}";
  }

  private String inDegree(long degreeId, int curricularYear, int semester) {
    return "[{\"degreeId\":" + degreeId + ",\"curricularYear\":" + curricularYear + ",\"semester\":" + semester + "}]";
  }

  private String edition(int academicYear, String teacherNumbers, String classSections) {
    return "{\"academicYear\":" + academicYear + ",\"teacherNumbers\":" + teacherNumbers + ",\"classSections\":"
        + classSections + "}";
  }

  private String validEdition() {
    return edition(YEAR, "[" + teacher.getNumber() + "]", "[\"T2\",\"T1\"]");
  }

  private String validSubject() {
    return newSubject(CODE, category.getId(), inDegree(degree.getId(), 2, 1), validEdition());
  }

  private String subjectWithEdition(String edition) {
    return newSubject(CODE, category.getId(), inDegree(degree.getId(), 2, 1), edition);
  }

  private MockHttpSession loginAsStaff() throws Exception {
    Staff staff = new Staff("it.staff@example.com", "it.staff@studymatch.com", "Test Staff", passwordEncoder.encode(PASSWORD));
    staff.setMustChangePassword(false);
    staffRepository.save(staff);
    return loginAs("it.staff@studymatch.com", PASSWORD);
  }

  private MockHttpSession loginAs(String email, String password) throws Exception {
    MvcResult result = mockMvc.perform(post("/users/login").with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
        .andReturn();
    assertEquals(200, result.getResponse().getStatus());
    return (MockHttpSession) result.getRequest().getSession(false);
  }

  private ResultActions registerSubject(MockHttpSession session, String body) throws Exception {
    return mockMvc.perform(post("/subjects/").with(csrf()).session(session)
        .contentType(MediaType.APPLICATION_JSON)
        .content(body));
  }

  private ResultActions registerEdition(MockHttpSession session, long subjectId, String body) throws Exception {
    return mockMvc.perform(post("/subjects/" + subjectId + "/editions").with(csrf()).session(session)
        .contentType(MediaType.APPLICATION_JSON)
        .content(body));
  }

  private Subject saveExistingSubject() {
    Subject subject = new Subject(CODE, "Existing Subject", 6, category);
    subject.addDegree(degree, 1, 1);
    return subjectRepository.save(subject);
  }

  // the staff registers subjects with their first edition

  @Test
  @DisplayName("Staff registers a subject: 201 with the edition, its teachers and classes, and the subject inside")
  void staffRegistersSubject() throws Exception {
    // Arrange
    MockHttpSession staff = loginAsStaff();

    // Act
    registerSubject(staff, validSubject())
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").isNumber())
        .andExpect(jsonPath("$.academicYear").value(YEAR))
        .andExpect(jsonPath("$.teachers.length()").value(1))
        .andExpect(jsonPath("$.teachers[0].number").value(teacher.getNumber()))
        .andExpect(jsonPath("$.teachers[0].name").value("IT Subject Teacher"))
        .andExpect(jsonPath("$.classSections[0]").value("T1"))
        .andExpect(jsonPath("$.classSections[1]").value("T2"))
        .andExpect(jsonPath("$.subject.id").isNumber())
        .andExpect(jsonPath("$.subject.code").value(CODE))
        .andExpect(jsonPath("$.subject.name").value("IT Test Subject"))
        .andExpect(jsonPath("$.subject.ects").value(6))
        .andExpect(jsonPath("$.subject.category.id").value(category.getId()))
        .andExpect(jsonPath("$.subject.category.name").value("IT Subject Category"))
        .andExpect(jsonPath("$.subject.degrees.length()").value(1))
        .andExpect(jsonPath("$.subject.degrees[0].degreeId").value(degree.getId()))
        .andExpect(jsonPath("$.subject.degrees[0].degreeName").value("IT Subject Degree"))
        .andExpect(jsonPath("$.subject.degrees[0].curricularYear").value(2))
        .andExpect(jsonPath("$.subject.degrees[0].semester").value(1));

    // Assert
    assertTrue(subjectRepository.findByCodeIgnoreCase(CODE).isPresent());
  }

  @Test
  @DisplayName("Classes are optional: a subject whose edition has none is 201")
  void editionWithoutClasses() throws Exception {
    // Arrange
    MockHttpSession staff = loginAsStaff();

    // Act + Assert
    registerSubject(staff, subjectWithEdition(edition(YEAR, "[" + teacher.getNumber() + "]", "[]")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.classSections.length()").value(0));
  }

  @Test
  @DisplayName("A code that already exists is 409, even with different case and spaces")
  void subjectCodeTaken() throws Exception {
    // Arrange
    Subject existing = saveExistingSubject();
    MockHttpSession staff = loginAsStaff();

    // Act + Assert
    registerSubject(staff, newSubject("  " + CODE.toLowerCase() + " ", category.getId(), inDegree(degree.getId(), 1, 1),
        validEdition()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("SUBJECT_ALREADY_EXISTS"))
        .andExpect(jsonPath("$.params.id").value(existing.getId()));
  }

  @Test
  @DisplayName("A subject with no degrees is a field error")
  void noDegrees() throws Exception {
    // Arrange
    MockHttpSession staff = loginAsStaff();

    // Act + Assert
    registerSubject(staff, newSubject(CODE, category.getId(), "[]", validEdition()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.fieldErrors[0].field").value("degrees"))
        .andExpect(jsonPath("$.fieldErrors[0].code").value("NOT_EMPTY"));
    assertFalse(subjectRepository.findByCodeIgnoreCase(CODE).isPresent());
  }

  @Test
  @DisplayName("A subject with no teachers is 400 AT_LEAST_ONE_TEACHER_REQUIRED")
  void noTeachers() throws Exception {
    // Arrange
    MockHttpSession staff = loginAsStaff();

    // Act + Assert - the subject's rollback isn't visible here, the test shares the service's transaction
    registerSubject(staff, subjectWithEdition(edition(YEAR, "[]", "[]")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("AT_LEAST_ONE_TEACHER_REQUIRED"));
  }

  @Test
  @DisplayName("A subject without its edition is a field error")
  void noEdition() throws Exception {
    // Arrange
    MockHttpSession staff = loginAsStaff();

    // Act + Assert
    registerSubject(staff, "{\"code\":\"" + CODE + "\",\"name\":\"IT Test Subject\",\"ects\":6,\"categoryId\":"
        + category.getId() + ",\"degrees\":" + inDegree(degree.getId(), 1, 1) + "}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors[0].field").value("edition"))
        .andExpect(jsonPath("$.fieldErrors[0].code").value("NOT_NULL"));
  }

  @Test
  @DisplayName("A bad semester inside a degree is a field error pointing at that degree")
  void badSemester() throws Exception {
    // Arrange
    MockHttpSession staff = loginAsStaff();

    // Act + Assert
    registerSubject(staff, newSubject(CODE, category.getId(), inDegree(degree.getId(), 1, 3), validEdition()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.fieldErrors[0].field").value("degrees[0].semester"))
        .andExpect(jsonPath("$.fieldErrors[0].code").value("MAX"));
  }

  @Test
  @DisplayName("A curricular year past the degree's last year is 400 CURRICULAR_YEAR_OUT_OF_RANGE")
  void curricularYearPastTheDegree() throws Exception {
    // Arrange - the degree has 3 curricular years
    MockHttpSession staff = loginAsStaff();

    // Act + Assert
    registerSubject(staff, newSubject(CODE, category.getId(), inDegree(degree.getId(), 4, 1), validEdition()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("CURRICULAR_YEAR_OUT_OF_RANGE"));
    assertFalse(subjectRepository.findByCodeIgnoreCase(CODE).isPresent());
  }

  @Test
  @DisplayName("The same degree twice is 409 SUBJECT_ALREADY_IN_DEGREE")
  void sameDegreeTwice() throws Exception {
    // Arrange
    MockHttpSession staff = loginAsStaff();
    String twice = "[{\"degreeId\":" + degree.getId() + ",\"curricularYear\":1,\"semester\":1},"
        + "{\"degreeId\":" + degree.getId() + ",\"curricularYear\":2,\"semester\":1}]";

    // Act + Assert
    registerSubject(staff, newSubject(CODE, category.getId(), twice, validEdition()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("SUBJECT_ALREADY_IN_DEGREE"));
  }

  @Test
  @DisplayName("The same class code twice is 409 CLASS_SECTION_ALREADY_EXISTS")
  void sameClassTwice() throws Exception {
    // Arrange
    MockHttpSession staff = loginAsStaff();

    // Act + Assert
    registerSubject(staff, subjectWithEdition(edition(YEAR, "[" + teacher.getNumber() + "]", "[\"T1\",\"T1\"]")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CLASS_SECTION_ALREADY_EXISTS"));
  }

  @Test
  @DisplayName("A category that doesn't exist is 404 NOT_FOUND")
  void unknownCategory() throws Exception {
    // Arrange
    MockHttpSession staff = loginAsStaff();

    // Act + Assert
    registerSubject(staff, newSubject(CODE, category.getId() + 1000, inDegree(degree.getId(), 1, 1), validEdition()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
  }

  @Test
  @DisplayName("A degree that doesn't exist is 404 NOT_FOUND")
  void unknownDegree() throws Exception {
    // Arrange
    MockHttpSession staff = loginAsStaff();

    // Act + Assert
    registerSubject(staff, newSubject(CODE, category.getId(), inDegree(degree.getId() + 1000, 1, 1), validEdition()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
  }

  @Test
  @DisplayName("An academic year that isn't registered is 404 NOT_FOUND")
  void unknownAcademicYear() throws Exception {
    // Arrange
    MockHttpSession staff = loginAsStaff();

    // Act + Assert
    registerSubject(staff, subjectWithEdition(edition(2199, "[" + teacher.getNumber() + "]", "[]")))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
  }

  @Test
  @DisplayName("A teacher that doesn't exist is 404 NOT_FOUND")
  void unknownTeacher() throws Exception {
    // Arrange
    MockHttpSession staff = loginAsStaff();

    // Act + Assert
    registerSubject(staff, subjectWithEdition(edition(YEAR, "[" + (teacher.getNumber() + 1000) + "]", "[]")))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
  }

  // a new edition of a subject that already exists

  @Test
  @DisplayName("Staff adds an edition for another year to an existing subject: 201 with the edition")
  void staffAddsEditionToExistingSubject() throws Exception {
    // Arrange
    Subject existing = saveExistingSubject();
    subjectEditionRepository.save(new SubjectEdition(existing, year, Set.of(teacher)));
    MockHttpSession staff = loginAsStaff();

    // Act + Assert
    registerEdition(staff, existing.getId(), edition(OTHER_YEAR, "[" + teacher.getNumber() + "]", "[\"T1\"]"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.academicYear").value(OTHER_YEAR))
        .andExpect(jsonPath("$.subject.id").value(existing.getId()))
        .andExpect(jsonPath("$.subject.degrees[0].degreeId").value(degree.getId()))
        .andExpect(jsonPath("$.classSections[0]").value("T1"));
  }

  @Test
  @DisplayName("A second edition for the same year is 409 SUBJECT_EDITION_ALREADY_EXISTS")
  void editionForSameYearTaken() throws Exception {
    // Arrange
    Subject existing = saveExistingSubject();
    subjectEditionRepository.save(new SubjectEdition(existing, year, Set.of(teacher)));
    MockHttpSession staff = loginAsStaff();

    // Act + Assert
    registerEdition(staff, existing.getId(), validEdition())
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("SUBJECT_EDITION_ALREADY_EXISTS"));
  }

  @Test
  @DisplayName("An edition for a subject that doesn't exist is 404 NOT_FOUND")
  void editionForUnknownSubject() throws Exception {
    // Arrange
    Subject existing = saveExistingSubject();
    MockHttpSession staff = loginAsStaff();

    // Act + Assert
    registerEdition(staff, existing.getId() + 1000, validEdition())
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
  }

  @Test
  @DisplayName("An edition without teachers is 400 AT_LEAST_ONE_TEACHER_REQUIRED")
  void editionWithoutTeachers() throws Exception {
    // Arrange
    Subject existing = saveExistingSubject();
    MockHttpSession staff = loginAsStaff();

    // Act + Assert
    registerEdition(staff, existing.getId(), edition(YEAR, "[]", "[]"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("AT_LEAST_ONE_TEACHER_REQUIRED"));
  }

  // only the staff

  @Test
  @DisplayName("A teacher can't register subjects or editions (403 ACCESS_DENIED)")
  void teacherIsDenied() throws Exception {
    // Arrange
    Subject existing = saveExistingSubject();
    Teacher loggedIn = new Teacher("it.teacher@example.com", "it.teacher@studymatch.com", "Test Teacher",
        passwordEncoder.encode(PASSWORD));
    loggedIn.setMustChangePassword(false);
    teacherRepository.save(loggedIn);
    MockHttpSession session = loginAs("it.teacher@studymatch.com", PASSWORD);

    // Act + Assert
    registerSubject(session, newSubject("ITSUBJ2", category.getId(), inDegree(degree.getId(), 1, 1), validEdition()))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    registerEdition(session, existing.getId(), validEdition())
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    assertFalse(subjectRepository.findByCodeIgnoreCase("ITSUBJ2").isPresent());
  }

  @Test
  @DisplayName("The admin can't register subjects either (403 ACCESS_DENIED)")
  void adminIsDenied() throws Exception {
    // Arrange
    Admin admin = new Admin("it.admin@example.com", "it.admin@studymatch.com", "Test Admin", passwordEncoder.encode(PASSWORD));
    admin.setMustChangePassword(false);
    adminRepository.save(admin);
    MockHttpSession session = loginAs("it.admin@studymatch.com", PASSWORD);

    // Act + Assert
    registerSubject(session, validSubject())
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
  }

  @Test
  @DisplayName("Without logging in it's 401")
  void notLoggedIn() throws Exception {
    mockMvc.perform(post("/subjects/").with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content(validSubject()))
        .andExpect(status().isUnauthorized());
  }

}
