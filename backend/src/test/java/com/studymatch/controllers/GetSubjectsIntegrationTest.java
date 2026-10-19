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
import com.studymatch.models.Degree;
import com.studymatch.models.Subject;
import com.studymatch.models.Teacher;
import com.studymatch.repositories.CategoryRepository;
import com.studymatch.repositories.DegreeRepository;
import com.studymatch.repositories.SubjectRepository;
import com.studymatch.repositories.TeacherRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class GetSubjectsIntegrationTest {

  private static final String PASSWORD = "Test1234";

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private TeacherRepository teacherRepository;

  @Autowired
  private CategoryRepository categoryRepository;

  @Autowired
  private DegreeRepository degreeRepository;

  @Autowired
  private SubjectRepository subjectRepository;

  @Autowired
  private PasswordEncoder passwordEncoder;

  private Subject alpha;
  private Degree degree;

  // saved out of order so the test shows the endpoint sorts them
  @BeforeEach
  void saveSubjects() {
    Category category = categoryRepository.save(new Category("IT Subject Category"));
    degree = degreeRepository.save(new Degree("IT Subject Degree", 3));
    subjectRepository.save(inDegree(new Subject("ITZETA", "IT Zeta Subject", 6, category)));
    alpha = subjectRepository.save(inDegree(new Subject("ITALPHA", "IT Alpha Subject", 5, category)));
    subjectRepository.save(inDegree(new Subject("ITMU", "IT Mu Subject", 4, category)));
  }

  private Subject inDegree(Subject subject) {
    subject.addDegree(degree, 2, 1);
    return subject;
  }

  private MockHttpSession loginAsTeacher() throws Exception {
    Teacher teacher = new Teacher("it.teacher@example.com", "it.teacher@studymatch.com", "Test Teacher",
        passwordEncoder.encode(PASSWORD));
    teacher.setMustChangePassword(false);
    teacherRepository.save(teacher);
    MvcResult result = mockMvc.perform(post("/users/login").with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"email\":\"it.teacher@studymatch.com\",\"password\":\"" + PASSWORD + "\"}"))
        .andReturn();
    assertEquals(200, result.getResponse().getStatus());
    return (MockHttpSession) result.getRequest().getSession(false);
  }

  // the test database may already hold other subjects, so only ours are checked
  private List<String> ourCodes(MvcResult result) throws Exception {
    List<String> codes = JsonPath.read(result.getResponse().getContentAsString(), "$[*].code");
    return codes.stream().filter(code -> code.startsWith("IT")).toList();
  }

  // list subjects

  @Test
  @DisplayName("Listing subjects is 200, sorted by code, each with its category and degrees")
  void teacherListsSubjects() throws Exception {
    // Arrange
    MockHttpSession session = loginAsTeacher();

    // Act
    MvcResult result = mockMvc.perform(get("/subjects/").session(session))
        .andExpect(status().isOk())
        .andReturn();

    // Assert
    assertEquals(List.of("ITALPHA", "ITMU", "ITZETA"), ourCodes(result));
    String json = result.getResponse().getContentAsString();
    List<String> degreeNames = JsonPath.read(json, "$[?(@.code == 'ITALPHA')].degrees[*].degreeName");
    assertEquals(List.of("IT Subject Degree"), degreeNames);
    List<String> categoryNames = JsonPath.read(json, "$[?(@.code == 'ITALPHA')].category.name");
    assertEquals(List.of("IT Subject Category"), categoryNames);
  }

  @Test
  @DisplayName("Listing without logging in is 401")
  void listNotLoggedIn() throws Exception {
    mockMvc.perform(get("/subjects/"))
        .andExpect(status().isUnauthorized());
  }

  // get one subject

  @Test
  @DisplayName("Getting a subject by id is 200 with its data, category and degrees")
  void getSubjectById() throws Exception {
    // Arrange
    MockHttpSession session = loginAsTeacher();

    // Act + Assert
    mockMvc.perform(get("/subjects/" + alpha.getId()).session(session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(alpha.getId()))
        .andExpect(jsonPath("$.code").value("ITALPHA"))
        .andExpect(jsonPath("$.name").value("IT Alpha Subject"))
        .andExpect(jsonPath("$.ects").value(5))
        .andExpect(jsonPath("$.category.name").value("IT Subject Category"))
        .andExpect(jsonPath("$.degrees[0].degreeId").value(degree.getId()))
        .andExpect(jsonPath("$.degrees[0].curricularYear").value(2))
        .andExpect(jsonPath("$.degrees[0].semester").value(1));
  }

  @Test
  @DisplayName("An id with no subject is 404 NOT_FOUND")
  void unknownIdIsNotFound() throws Exception {
    // Arrange
    MockHttpSession session = loginAsTeacher();
    long missingId = alpha.getId() + 1000;

    // Act + Assert
    mockMvc.perform(get("/subjects/" + missingId).session(session))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
  }

  @Test
  @DisplayName("Getting a subject without logging in is 401")
  void getNotLoggedIn() throws Exception {
    mockMvc.perform(get("/subjects/" + alpha.getId()))
        .andExpect(status().isUnauthorized());
  }

}
