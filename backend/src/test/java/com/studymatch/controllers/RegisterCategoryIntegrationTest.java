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
import com.studymatch.models.Category;
import com.studymatch.models.Staff;
import com.studymatch.models.Teacher;
import com.studymatch.repositories.AdminRepository;
import com.studymatch.repositories.CategoryRepository;
import com.studymatch.repositories.StaffRepository;
import com.studymatch.repositories.TeacherRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RegisterCategoryIntegrationTest {

  private static final String PASSWORD = "Test1234";
  private static final String NAME = "IT Test Category";
  private static final String NEW_CATEGORY = "{\"name\":\"" + NAME + "\"}";

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

  private ResultActions registerCategory(MockHttpSession session, String body) throws Exception {
    return mockMvc.perform(post("/categories/register").with(csrf()).session(session)
        .contentType(MediaType.APPLICATION_JSON)
        .content(body));
  }

  // the staff registers categories

  @Test
  @DisplayName("Staff registers a category: 201 with its data, and it's saved")
  void staffRegistersCategory() throws Exception {
    // Arrange
    MockHttpSession staff = loginAsStaff();

    // Act
    registerCategory(staff, NEW_CATEGORY)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").isNumber())
        .andExpect(jsonPath("$.name").value(NAME));

    // Assert
    assertTrue(categoryRepository.existsByNameIgnoreCase(NAME));
  }

  @Test
  @DisplayName("A name that already exists is 409, even with different case and spaces")
  void categoryNameTaken() throws Exception {
    // Arrange
    categoryRepository.save(new Category(NAME));
    MockHttpSession staff = loginAsStaff();

    // Act + Assert
    registerCategory(staff, "{\"name\":\"  " + NAME.toLowerCase() + " \"}")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CATEGORY_ALREADY_EXISTS"));
  }

  @Test
  @DisplayName("A blank name is a field error")
  void blankName() throws Exception {
    // Arrange
    MockHttpSession staff = loginAsStaff();

    // Act + Assert
    registerCategory(staff, "{\"name\":\"   \"}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.fieldErrors[0].field").value("name"))
        .andExpect(jsonPath("$.fieldErrors[0].code").value("NOT_BLANK"));
  }

  // only the staff

  @Test
  @DisplayName("A teacher can't register categories (403 ACCESS_DENIED)")
  void teacherIsDenied() throws Exception {
    // Arrange
    Teacher teacher = new Teacher("it.teacher@example.com", "it.teacher@studymatch.com", "Test Teacher",
        passwordEncoder.encode(PASSWORD));
    teacher.setMustChangePassword(false);
    teacherRepository.save(teacher);
    MockHttpSession session = loginAs("it.teacher@studymatch.com", PASSWORD);

    // Act + Assert
    registerCategory(session, NEW_CATEGORY)
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    assertFalse(categoryRepository.existsByNameIgnoreCase(NAME));
  }

  @Test
  @DisplayName("The admin can't register categories either (403 ACCESS_DENIED)")
  void adminIsDenied() throws Exception {
    // Arrange
    Admin admin = new Admin("it.admin@example.com", "it.admin@studymatch.com", "Test Admin", passwordEncoder.encode(PASSWORD));
    admin.setMustChangePassword(false);
    adminRepository.save(admin);
    MockHttpSession session = loginAs("it.admin@studymatch.com", PASSWORD);

    // Act + Assert
    registerCategory(session, NEW_CATEGORY)
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
  }

  @Test
  @DisplayName("Without logging in it's 401")
  void notLoggedIn() throws Exception {
    mockMvc.perform(post("/categories/register").with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content(NEW_CATEGORY))
        .andExpect(status().isUnauthorized());
  }

}
