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
import com.studymatch.models.Staff;
import com.studymatch.models.Teacher;
import com.studymatch.repositories.StaffRepository;
import com.studymatch.repositories.TeacherRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class GetTeachersIntegrationTest {

  private static final String PASSWORD = "Test1234";

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private StaffRepository staffRepository;

  @Autowired
  private TeacherRepository teacherRepository;

  @Autowired
  private PasswordEncoder passwordEncoder;

  // saved out of order so the test shows the endpoint sorts them
  @BeforeEach
  void saveTeachers() {
    saveTeacher("zeta", "IT Zeta Teacher");
    saveTeacher("alpha", "IT Alpha Teacher");
    saveTeacher("mu", "IT Mu Teacher");
  }

  private Teacher saveTeacher(String login, String name) {
    Teacher teacher = new Teacher("it." + login + "@example.com", "it." + login + "@studymatch.com", name,
        passwordEncoder.encode(PASSWORD));
    teacher.setMustChangePassword(false);
    return teacherRepository.save(teacher);
  }

  private MockHttpSession loginAs(String email) throws Exception {
    MvcResult result = mockMvc.perform(post("/users/login").with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
        .andReturn();
    assertEquals(200, result.getResponse().getStatus());
    return (MockHttpSession) result.getRequest().getSession(false);
  }

  // the test database may already hold other teachers, so only ours are checked
  private List<String> ourNames(MvcResult result) throws Exception {
    List<String> names = JsonPath.read(result.getResponse().getContentAsString(), "$[*].name");
    return names.stream().filter(name -> name.startsWith("IT ") && name.endsWith(" Teacher")).toList();
  }

  @Test
  @DisplayName("Staff lists teachers: 200, sorted by name, with number, name and institutional email")
  void staffListsTeachers() throws Exception {
    // Arrange
    Staff staff = new Staff("it.staff@example.com", "it.staff@studymatch.com", "Test Staff", passwordEncoder.encode(PASSWORD));
    staff.setMustChangePassword(false);
    staffRepository.save(staff);
    MockHttpSession session = loginAs("it.staff@studymatch.com");

    // Act
    MvcResult result = mockMvc.perform(get("/teachers/").session(session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].number").isNumber())
        .andExpect(jsonPath("$[0].institutionalEmail").isString())
        .andReturn();

    // Assert
    assertEquals(List.of("IT Alpha Teacher", "IT Mu Teacher", "IT Zeta Teacher"), ourNames(result));
    List<String> alphaEmails = JsonPath.read(result.getResponse().getContentAsString(),
        "$[?(@.name == 'IT Alpha Teacher')].institutionalEmail");
    assertEquals(List.of("it.alpha@studymatch.com"), alphaEmails);
  }

  @Test
  @DisplayName("A teacher can't list the teachers (403 ACCESS_DENIED)")
  void teacherIsDenied() throws Exception {
    // Arrange
    MockHttpSession session = loginAs("it.alpha@studymatch.com");

    // Act + Assert
    mockMvc.perform(get("/teachers/").session(session))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
  }

  @Test
  @DisplayName("Listing without logging in is 401")
  void notLoggedIn() throws Exception {
    mockMvc.perform(get("/teachers/"))
        .andExpect(status().isUnauthorized());
  }

}
