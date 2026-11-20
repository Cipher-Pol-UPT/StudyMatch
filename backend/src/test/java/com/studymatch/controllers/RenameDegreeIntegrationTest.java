package com.studymatch.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import com.studymatch.models.Degree;
import com.studymatch.models.Staff;
import com.studymatch.models.Student;
import com.studymatch.models.Teacher;
import com.studymatch.repositories.AdminRepository;
import com.studymatch.repositories.DegreeRepository;
import com.studymatch.repositories.StaffRepository;
import com.studymatch.repositories.StudentRepository;
import com.studymatch.repositories.TeacherRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RenameDegreeIntegrationTest {

  private static final String PASSWORD = "Test1234";
  private static final String NAME = "IT Test Degree";
  private static final String NEW_NAME = "IT Renamed Degree";
  private static final String RENAME = "{\"name\":\"" + NEW_NAME + "\"}";

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private StaffRepository staffRepository;

  @Autowired
  private TeacherRepository teacherRepository;

  @Autowired
  private AdminRepository adminRepository;

  @Autowired
  private DegreeRepository degreeRepository;

  @Autowired
  private StudentRepository studentRepository;

  @Autowired
  private PasswordEncoder passwordEncoder;

  private Degree degree;

  @BeforeEach
  void setUp() {
    degree = degreeRepository.save(new Degree(NAME, 3));
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

  private ResultActions renameDegree(MockHttpSession session, Long id, String body) throws Exception {
    return mockMvc.perform(put("/degrees/" + id).with(csrf()).session(session)
        .contentType(MediaType.APPLICATION_JSON)
        .content(body));
  }

  private String nameInDatabase() {
    return degreeRepository.findById(degree.getId()).orElseThrow().getName();
  }

  // the staff renames degrees

  @Test
  @DisplayName("Staff renames a degree: 200 with its data, and the new name is saved")
  void staffRenamesDegree() throws Exception {
    // Arrange
    MockHttpSession staff = loginAsStaff();

    // Act
    renameDegree(staff, degree.getId(), "{\"name\":\"  " + NEW_NAME + " \"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(degree.getId()))
        .andExpect(jsonPath("$.name").value(NEW_NAME))
        .andExpect(jsonPath("$.curricularYears").value(3));

    // Assert
    assertEquals(NEW_NAME, nameInDatabase());
  }

  @Test
  @DisplayName("Students of the degree stay linked to it after the rename")
  void studentsStayLinked() throws Exception {
    // Arrange
    Student student = studentRepository.save(new Student("it.student@example.com", "Test Student", "hash", degree, 1));
    MockHttpSession staff = loginAsStaff();

    // Act
    renameDegree(staff, degree.getId(), RENAME).andExpect(status().isOk());

    // Assert
    Student reloaded = studentRepository.findById(student.getNumber()).orElseThrow();
    assertEquals(degree.getId(), reloaded.getDegree().getId());
    assertEquals(NEW_NAME, reloaded.getDegree().getName());
  }

  @Test
  @DisplayName("Changing only the case of its own name is allowed")
  void caseOnlyRename() throws Exception {
    // Arrange
    MockHttpSession staff = loginAsStaff();

    // Act + Assert
    renameDegree(staff, degree.getId(), "{\"name\":\"" + NAME.toUpperCase() + "\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value(NAME.toUpperCase()));
    assertEquals(NAME.toUpperCase(), nameInDatabase());
  }

  @Test
  @DisplayName("Another degree's name is 409, even with different case and spaces, and the name is kept")
  void degreeNameTaken() throws Exception {
    // Arrange
    degreeRepository.save(new Degree(NEW_NAME, 3));
    MockHttpSession staff = loginAsStaff();

    // Act + Assert
    renameDegree(staff, degree.getId(), "{\"name\":\"  " + NEW_NAME.toLowerCase() + " \"}")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("DEGREE_ALREADY_EXISTS"));
    assertEquals(NAME, nameInDatabase());
  }

  @Test
  @DisplayName("A blank name is a field error, and the name is kept")
  void blankName() throws Exception {
    // Arrange
    MockHttpSession staff = loginAsStaff();

    // Act + Assert
    renameDegree(staff, degree.getId(), "{\"name\":\"   \"}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.fieldErrors[0].field").value("name"))
        .andExpect(jsonPath("$.fieldErrors[0].code").value("NOT_BLANK"));
    assertEquals(NAME, nameInDatabase());
  }

  @Test
  @DisplayName("A name over 100 characters is a field error, not a database crash")
  void nameTooLong() throws Exception {
    // Arrange
    MockHttpSession staff = loginAsStaff();

    // Act + Assert
    renameDegree(staff, degree.getId(), "{\"name\":\"" + "a".repeat(101) + "\"}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.fieldErrors[0].field").value("name"))
        .andExpect(jsonPath("$.fieldErrors[0].code").value("SIZE_MAX"));
  }

  @Test
  @DisplayName("A degree that doesn't exist is 404")
  void unknownDegree() throws Exception {
    // Arrange
    MockHttpSession staff = loginAsStaff();

    // Act + Assert
    renameDegree(staff, Long.MAX_VALUE, RENAME)
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
  }

  // only the staff

  @Test
  @DisplayName("A teacher can't rename degrees (403 ACCESS_DENIED)")
  void teacherIsDenied() throws Exception {
    // Arrange
    Teacher teacher = new Teacher("it.teacher@example.com", "it.teacher@studymatch.com", "Test Teacher",
        passwordEncoder.encode(PASSWORD));
    teacher.setMustChangePassword(false);
    teacherRepository.save(teacher);
    MockHttpSession session = loginAs("it.teacher@studymatch.com", PASSWORD);

    // Act + Assert
    renameDegree(session, degree.getId(), RENAME)
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    assertEquals(NAME, nameInDatabase());
  }

  @Test
  @DisplayName("The admin can't rename degrees either (403 ACCESS_DENIED)")
  void adminIsDenied() throws Exception {
    // Arrange
    Admin admin = new Admin("it.admin@example.com", "it.admin@studymatch.com", "Test Admin", passwordEncoder.encode(PASSWORD));
    admin.setMustChangePassword(false);
    adminRepository.save(admin);
    MockHttpSession session = loginAs("it.admin@studymatch.com", PASSWORD);

    // Act + Assert
    renameDegree(session, degree.getId(), RENAME)
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
  }

  @Test
  @DisplayName("Without logging in it's 401")
  void notLoggedIn() throws Exception {
    mockMvc.perform(put("/degrees/" + degree.getId()).with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content(RENAME))
        .andExpect(status().isUnauthorized());
  }

}
