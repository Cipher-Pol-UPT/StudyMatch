package com.studymatch.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

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

import com.jayway.jsonpath.JsonPath;
import com.studymatch.models.Category;
import com.studymatch.models.Staff;
import com.studymatch.models.Teacher;
import com.studymatch.repositories.CategoryRepository;
import com.studymatch.repositories.StaffRepository;
import com.studymatch.repositories.TeacherRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class GetCategoriesIntegrationTest {

  private static final String PASSWORD = "Test1234";

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private StaffRepository staffRepository;

  @Autowired
  private TeacherRepository teacherRepository;

  @Autowired
  private CategoryRepository categoryRepository;

  @Autowired
  private PasswordEncoder passwordEncoder;

  private Category alpha;

  // saved out of order so the test shows the endpoint sorts them
  @BeforeEach
  void saveCategories() {
    categoryRepository.save(new Category("IT Zeta Category"));
    alpha = categoryRepository.save(new Category("IT Alpha Category"));
    categoryRepository.save(new Category("IT Mu Category"));
  }

  private MockHttpSession loginAsTeacher() throws Exception {
    Teacher teacher = new Teacher("it.teacher@example.com", "it.teacher@studymatch.com", "Test Teacher",
        passwordEncoder.encode(PASSWORD));
    teacher.setMustChangePassword(false);
    teacherRepository.save(teacher);
    return loginAs("it.teacher@studymatch.com", PASSWORD);
  }

  private MockHttpSession loginAs(String email, String password) throws Exception {
    MvcResult result = mockMvc.perform(post("/users/login").with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
        .andReturn();
    assertEquals(200, result.getResponse().getStatus());
    return (MockHttpSession) result.getRequest().getSession(false);
  }

  // the test database may already hold other categories, so only ours are checked
  private List<String> ourNames(MvcResult result) throws Exception {
    List<String> names = JsonPath.read(result.getResponse().getContentAsString(), "$[*].name");
    return names.stream().filter(name -> name.startsWith("IT ") && name.endsWith(" Category")).toList();
  }

  @Test
  @DisplayName("Staff lists categories: 200, sorted by name, with id and name")
  void staffListsCategories() throws Exception {
    // Arrange
    Staff staff = new Staff("it.staff@example.com", "it.staff@studymatch.com", "Test Staff", passwordEncoder.encode(PASSWORD));
    staff.setMustChangePassword(false);
    staffRepository.save(staff);
    MockHttpSession session = loginAs("it.staff@studymatch.com", PASSWORD);

    // Act
    MvcResult result = mockMvc.perform(get("/categories/").session(session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").isNumber())
        .andExpect(jsonPath("$[0].name").isString())
        .andReturn();

    // Assert
    assertEquals(List.of("IT Alpha Category", "IT Mu Category", "IT Zeta Category"), ourNames(result));
  }

  @Test
  @DisplayName("Not only the staff: a teacher can list categories too")
  void teacherListsCategories() throws Exception {
    // Arrange
    MockHttpSession session = loginAsTeacher();

    // Act
    MvcResult result = mockMvc.perform(get("/categories/").session(session))
        .andExpect(status().isOk())
        .andReturn();

    // Assert
    assertEquals(List.of("IT Alpha Category", "IT Mu Category", "IT Zeta Category"), ourNames(result));
  }

  @Test
  @DisplayName("Listing without logging in is 401")
  void listNotLoggedIn() throws Exception {
    mockMvc.perform(get("/categories/"))
        .andExpect(status().isUnauthorized());
  }

  // get one category

  @Test
  @DisplayName("Getting a category by id is 200 with its id and name")
  void getCategoryById() throws Exception {
    // Arrange
    MockHttpSession session = loginAsTeacher();

    // Act + Assert
    mockMvc.perform(get("/categories/" + alpha.getId()).session(session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(alpha.getId()))
        .andExpect(jsonPath("$.name").value("IT Alpha Category"));
  }

  @Test
  @DisplayName("An id with no category is 404 NOT_FOUND")
  void unknownIdIsNotFound() throws Exception {
    // Arrange
    MockHttpSession session = loginAsTeacher();
    long missingId = alpha.getId() + 1000;

    // Act + Assert
    mockMvc.perform(get("/categories/" + missingId).session(session))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
  }

  @Test
  @DisplayName("Getting a category without logging in is 401")
  void getNotLoggedIn() throws Exception {
    mockMvc.perform(get("/categories/" + alpha.getId()))
        .andExpect(status().isUnauthorized());
  }

}
