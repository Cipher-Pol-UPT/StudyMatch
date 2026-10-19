package com.studymatch.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import com.studymatch.models.Teacher;
import com.studymatch.repositories.AdminRepository;
import com.studymatch.repositories.DegreeRepository;
import com.studymatch.repositories.StaffRepository;
import com.studymatch.repositories.TeacherRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RegisterDegreeIntegrationTest {

  private static final String PASSWORD = "Test1234";
  private static final String NAME = "IT Test Degree";
  private static final String NEW_DEGREE = "{\"name\":\"" + NAME + "\"}";

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
  private PasswordEncoder passwordEncoder;

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

  private ResultActions registerDegree(MockHttpSession session, String body) throws Exception {
    return mockMvc.perform(post("/degrees/register").with(csrf()).session(session)
        .contentType(MediaType.APPLICATION_JSON)
        .content(body));
  }

  // the staff registers degrees

  @Test
  @DisplayName("Staff registers a degree: 201 with its data, and it's saved")
  void staffRegistersDegree() throws Exception {
    // Arrange
    MockHttpSession staff = loginAsStaff();

    // Act
    registerDegree(staff, NEW_DEGREE)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").isNumber())
        .andExpect(jsonPath("$.name").value(NAME));

    // Assert
    assertTrue(degreeRepository.existsByNameIgnoreCase(NAME));
  }

  @Test
  @DisplayName("A name that already exists is 409, even with different case and spaces")
  void degreeNameTaken() throws Exception {
    // Arrange
    degreeRepository.save(new Degree(NAME));
    MockHttpSession staff = loginAsStaff();

    // Act + Assert
    registerDegree(staff, "{\"name\":\"  " + NAME.toLowerCase() + " \"}")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("DEGREE_ALREADY_EXISTS"));
  }

  @Test
  @DisplayName("A blank name is a field error")
  void blankName() throws Exception {
    // Arrange
    MockHttpSession staff = loginAsStaff();

    // Act + Assert
    registerDegree(staff, "{\"name\":\"   \"}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.fieldErrors[0].field").value("name"))
        .andExpect(jsonPath("$.fieldErrors[0].code").value("NOT_BLANK"));
  }

  @Test
  @DisplayName("A name over 100 characters is a field error, not a database crash")
  void nameTooLong() throws Exception {
    // Arrange
    MockHttpSession staff = loginAsStaff();

    // Act + Assert
    registerDegree(staff, "{\"name\":\"" + "a".repeat(101) + "\"}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.fieldErrors[0].field").value("name"))
        .andExpect(jsonPath("$.fieldErrors[0].code").value("SIZE_MAX"));
  }

  // only the staff

  @Test
  @DisplayName("A teacher can't register degrees (403 ACCESS_DENIED)")
  void teacherIsDenied() throws Exception {
    // Arrange
    Teacher teacher = new Teacher("it.teacher@example.com", "it.teacher@studymatch.com", "Test Teacher",
        passwordEncoder.encode(PASSWORD));
    teacher.setMustChangePassword(false);
    teacherRepository.save(teacher);
    MockHttpSession session = loginAs("it.teacher@studymatch.com", PASSWORD);

    // Act + Assert
    registerDegree(session, NEW_DEGREE)
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    assertFalse(degreeRepository.existsByNameIgnoreCase(NAME));
  }

  @Test
  @DisplayName("The admin can't register degrees either (403 ACCESS_DENIED)")
  void adminIsDenied() throws Exception {
    // Arrange
    Admin admin = new Admin("it.admin@example.com", "it.admin@studymatch.com", "Test Admin", passwordEncoder.encode(PASSWORD));
    admin.setMustChangePassword(false);
    adminRepository.save(admin);
    MockHttpSession session = loginAs("it.admin@studymatch.com", PASSWORD);

    // Act + Assert
    registerDegree(session, NEW_DEGREE)
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
  }

  @Test
  @DisplayName("Without logging in it's 401")
  void notLoggedIn() throws Exception {
    mockMvc.perform(post("/degrees/register").with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content(NEW_DEGREE))
        .andExpect(status().isUnauthorized());
  }

}
