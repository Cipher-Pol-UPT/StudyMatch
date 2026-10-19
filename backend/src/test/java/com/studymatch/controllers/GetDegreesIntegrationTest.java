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
import com.studymatch.models.Degree;
import com.studymatch.models.Staff;
import com.studymatch.models.Teacher;
import com.studymatch.repositories.DegreeRepository;
import com.studymatch.repositories.StaffRepository;
import com.studymatch.repositories.TeacherRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class GetDegreesIntegrationTest {

  private static final String PASSWORD = "Test1234";

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private StaffRepository staffRepository;

  @Autowired
  private TeacherRepository teacherRepository;

  @Autowired
  private DegreeRepository degreeRepository;

  @Autowired
  private PasswordEncoder passwordEncoder;

  private Degree alpha;

  // saved out of order so the test shows the endpoint sorts them
  @BeforeEach
  void saveDegrees() {
    degreeRepository.save(new Degree("IT Zeta Degree"));
    alpha = degreeRepository.save(new Degree("IT Alpha Degree"));
    degreeRepository.save(new Degree("IT Mu Degree"));
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

  // the test database may already hold other degrees, so only ours are checked
  private List<String> ourNames(MvcResult result) throws Exception {
    List<String> names = JsonPath.read(result.getResponse().getContentAsString(), "$[*].name");
    return names.stream().filter(name -> name.startsWith("IT ") && name.endsWith(" Degree")).toList();
  }

  @Test
  @DisplayName("Staff lists degrees: 200, sorted by name, with id and name")
  void staffListsDegrees() throws Exception {
    // Arrange
    Staff staff = new Staff("it.staff@example.com", "it.staff@studymatch.com", "Test Staff", passwordEncoder.encode(PASSWORD));
    staff.setMustChangePassword(false);
    staffRepository.save(staff);
    MockHttpSession session = loginAs("it.staff@studymatch.com", PASSWORD);

    // Act
    MvcResult result = mockMvc.perform(get("/degrees/").session(session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").isNumber())
        .andExpect(jsonPath("$[0].name").isString())
        .andReturn();

    // Assert
    assertEquals(List.of("IT Alpha Degree", "IT Mu Degree", "IT Zeta Degree"), ourNames(result));
  }

  @Test
  @DisplayName("Not only the staff: a teacher can list degrees too")
  void teacherListsDegrees() throws Exception {
    // Arrange
    MockHttpSession session = loginAsTeacher();

    // Act
    MvcResult result = mockMvc.perform(get("/degrees/").session(session))
        .andExpect(status().isOk())
        .andReturn();

    // Assert
    assertEquals(List.of("IT Alpha Degree", "IT Mu Degree", "IT Zeta Degree"), ourNames(result));
  }

  @Test
  @DisplayName("Listing without logging in is 401")
  void listNotLoggedIn() throws Exception {
    mockMvc.perform(get("/degrees/"))
        .andExpect(status().isUnauthorized());
  }

  // get one degree

  @Test
  @DisplayName("Getting a degree by id is 200 with its id and name")
  void getDegreeById() throws Exception {
    // Arrange
    MockHttpSession session = loginAsTeacher();

    // Act + Assert
    mockMvc.perform(get("/degrees/" + alpha.getId()).session(session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(alpha.getId()))
        .andExpect(jsonPath("$.name").value("IT Alpha Degree"));
  }

  @Test
  @DisplayName("An id with no degree is 404 NOT_FOUND")
  void unknownIdIsNotFound() throws Exception {
    // Arrange
    MockHttpSession session = loginAsTeacher();
    long missingId = alpha.getId() + 1000;

    // Act + Assert
    mockMvc.perform(get("/degrees/" + missingId).session(session))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
  }

  @Test
  @DisplayName("Getting a degree without logging in is 401")
  void getNotLoggedIn() throws Exception {
    mockMvc.perform(get("/degrees/" + alpha.getId()))
        .andExpect(status().isUnauthorized());
  }

}
