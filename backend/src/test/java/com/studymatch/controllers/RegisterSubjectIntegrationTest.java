package com.studymatch.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.studymatch.models.Admin;
import com.studymatch.models.Category;
import com.studymatch.models.Degree;
import com.studymatch.models.Staff;
import com.studymatch.models.Subject;
import com.studymatch.models.Teacher;
import com.studymatch.repositories.AdminRepository;
import com.studymatch.repositories.CategoryRepository;
import com.studymatch.repositories.DegreeRepository;
import com.studymatch.repositories.StaffRepository;
import com.studymatch.repositories.SubjectRepository;
import com.studymatch.repositories.TeacherRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RegisterSubjectIntegrationTest {

  private static final String PASSWORD = "Test1234";
  private static final String CODE = "ITSUBJ1";

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
  private SubjectRepository subjectRepository;

  @Autowired
  private PasswordEncoder passwordEncoder;

  private Category category;
  private Degree degree;

  @BeforeEach
  void saveCategoryAndDegree() {
    category = categoryRepository.save(new Category("IT Subject Category"));
    degree = degreeRepository.save(new Degree("IT Subject Degree", 3));
  }

  private String newSubject(String code, long categoryId, String degrees) {
    return "{\"code\":\"" + code + "\",\"name\":\"IT Test Subject\",\"ects\":6,\"categoryId\":" + categoryId
        + ",\"degrees\":" + degrees + "}";
  }

  private String inDegree(long degreeId, int curricularYear, int semester) {
    return "[{\"degreeId\":" + degreeId + ",\"curricularYear\":" + curricularYear + ",\"semester\":" + semester + "}]";
  }

  private String validSubject() {
    return newSubject(CODE, category.getId(), inDegree(degree.getId(), 2, 1));
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

  // the staff registers subjects

  @Test
  @DisplayName("Staff registers a subject in a degree: 201 with its data, category and degree, and it's saved")
  void staffRegistersSubject() throws Exception {
    // Arrange
    MockHttpSession staff = loginAsStaff();

    // Act
    registerSubject(staff, validSubject())
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").isNumber())
        .andExpect(jsonPath("$.code").value(CODE))
        .andExpect(jsonPath("$.name").value("IT Test Subject"))
        .andExpect(jsonPath("$.ects").value(6))
        .andExpect(jsonPath("$.category.id").value(category.getId()))
        .andExpect(jsonPath("$.category.name").value("IT Subject Category"))
        .andExpect(jsonPath("$.degrees.length()").value(1))
        .andExpect(jsonPath("$.degrees[0].degreeId").value(degree.getId()))
        .andExpect(jsonPath("$.degrees[0].degreeName").value("IT Subject Degree"))
        .andExpect(jsonPath("$.degrees[0].curricularYear").value(2))
        .andExpect(jsonPath("$.degrees[0].semester").value(1));

    // Assert
    assertTrue(subjectRepository.existsByCodeIgnoreCase(CODE));
  }

  @Test
  @DisplayName("A code that already exists is 409, even with different case and spaces")
  void subjectCodeTaken() throws Exception {
    // Arrange
    subjectRepository.save(new Subject(CODE, "Existing Subject", 6, category));
    MockHttpSession staff = loginAsStaff();

    // Act + Assert
    registerSubject(staff, newSubject("  " + CODE.toLowerCase() + " ", category.getId(), inDegree(degree.getId(), 1, 1)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("SUBJECT_ALREADY_EXISTS"));
  }

  @Test
  @DisplayName("A subject with no degrees is a field error")
  void noDegrees() throws Exception {
    // Arrange
    MockHttpSession staff = loginAsStaff();

    // Act + Assert
    registerSubject(staff, newSubject(CODE, category.getId(), "[]"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.fieldErrors[0].field").value("degrees"))
        .andExpect(jsonPath("$.fieldErrors[0].code").value("NOT_EMPTY"));
    assertFalse(subjectRepository.existsByCodeIgnoreCase(CODE));
  }

  @Test
  @DisplayName("A bad semester inside a degree is a field error pointing at that degree")
  void badSemester() throws Exception {
    // Arrange
    MockHttpSession staff = loginAsStaff();

    // Act + Assert
    registerSubject(staff, newSubject(CODE, category.getId(), inDegree(degree.getId(), 1, 3)))
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
    registerSubject(staff, newSubject(CODE, category.getId(), inDegree(degree.getId(), 4, 1)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("CURRICULAR_YEAR_OUT_OF_RANGE"));
    assertFalse(subjectRepository.existsByCodeIgnoreCase(CODE));
  }

  @Test
  @DisplayName("The same degree twice is 409 SUBJECT_ALREADY_IN_DEGREE")
  void sameDegreeTwice() throws Exception {
    // Arrange
    MockHttpSession staff = loginAsStaff();
    String twice = "[{\"degreeId\":" + degree.getId() + ",\"curricularYear\":1,\"semester\":1},"
        + "{\"degreeId\":" + degree.getId() + ",\"curricularYear\":2,\"semester\":1}]";

    // Act + Assert
    registerSubject(staff, newSubject(CODE, category.getId(), twice))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("SUBJECT_ALREADY_IN_DEGREE"));
  }

  @Test
  @DisplayName("A category that doesn't exist is 404 NOT_FOUND")
  void unknownCategory() throws Exception {
    // Arrange
    MockHttpSession staff = loginAsStaff();

    // Act + Assert
    registerSubject(staff, newSubject(CODE, category.getId() + 1000, inDegree(degree.getId(), 1, 1)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
  }

  @Test
  @DisplayName("A degree that doesn't exist is 404 NOT_FOUND")
  void unknownDegree() throws Exception {
    // Arrange
    MockHttpSession staff = loginAsStaff();

    // Act + Assert
    registerSubject(staff, newSubject(CODE, category.getId(), inDegree(degree.getId() + 1000, 1, 1)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
  }

  // only the staff

  @Test
  @DisplayName("A teacher can't register subjects (403 ACCESS_DENIED)")
  void teacherIsDenied() throws Exception {
    // Arrange
    Teacher teacher = new Teacher("it.teacher@example.com", "it.teacher@studymatch.com", "Test Teacher",
        passwordEncoder.encode(PASSWORD));
    teacher.setMustChangePassword(false);
    teacherRepository.save(teacher);
    MockHttpSession session = loginAs("it.teacher@studymatch.com", PASSWORD);

    // Act + Assert
    registerSubject(session, validSubject())
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    assertFalse(subjectRepository.existsByCodeIgnoreCase(CODE));
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
