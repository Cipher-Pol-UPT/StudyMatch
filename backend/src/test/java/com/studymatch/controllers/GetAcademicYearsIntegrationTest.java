package com.studymatch.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import com.studymatch.models.AcademicYear;
import com.studymatch.models.Teacher;
import com.studymatch.repositories.AcademicYearRepository;
import com.studymatch.repositories.TeacherRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class GetAcademicYearsIntegrationTest {

  private static final String PASSWORD = "Test1234";

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private TeacherRepository teacherRepository;

  @Autowired
  private AcademicYearRepository academicYearRepository;

  @Autowired
  private PasswordEncoder passwordEncoder;

  // far in the future so they can't clash with real academic years, saved out of order
  @BeforeEach
  void saveAcademicYears() {
    academicYearRepository.save(new AcademicYear(2091));
    academicYearRepository.save(new AcademicYear(2093));
    academicYearRepository.save(new AcademicYear(2092));
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

  @Test
  @DisplayName("Any logged-in user lists academic years: 200, newest first")
  void listAcademicYears() throws Exception {
    // Arrange
    MockHttpSession session = loginAsTeacher();

    // Act
    MvcResult result = mockMvc.perform(get("/academic-years/").session(session))
        .andExpect(status().isOk())
        .andReturn();

    // Assert - the test database may already hold real years, so only ours are checked
    List<Integer> years = JsonPath.read(result.getResponse().getContentAsString(), "$[*].startYear");
    assertEquals(List.of(2093, 2092, 2091), years.stream().filter(year -> year >= 2091 && year <= 2093).toList());
  }

  @Test
  @DisplayName("Listing without logging in is 401")
  void notLoggedIn() throws Exception {
    mockMvc.perform(get("/academic-years/"))
        .andExpect(status().isUnauthorized());
  }

}
