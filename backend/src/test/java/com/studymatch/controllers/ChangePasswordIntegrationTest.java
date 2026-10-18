package com.studymatch.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

import com.studymatch.models.Teacher;
import com.studymatch.repositories.TeacherRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ChangePasswordIntegrationTest {

  private static final String EMAIL = "it.teacher@studymatch.com";
  private static final String CURRENT = "Current123";
  private static final String NEW = "NewPass123";

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private TeacherRepository teacherRepository;

  @Autowired
  private PasswordEncoder passwordEncoder;

  // test user (rolled back after each test)
  private void saveTeacher(boolean mustChangePassword) {
    Teacher teacher = new Teacher("it.teacher@example.com", EMAIL, "Test Teacher", passwordEncoder.encode(CURRENT));
    teacher.setMustChangePassword(mustChangePassword);
    teacherRepository.save(teacher);
  }

  private MvcResult login(String password) throws Exception {
    return mockMvc.perform(post("/users/login").with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"email\":\"" + EMAIL + "\",\"password\":\"" + password + "\"}"))
        .andReturn();
  }

  private MockHttpSession loginAndGetSession() throws Exception {
    MvcResult result = login(CURRENT);
    assertEquals(200, result.getResponse().getStatus());
    return (MockHttpSession) result.getRequest().getSession(false);
  }

  private ResultActions changePassword(MockHttpSession session, String body) throws Exception {
    return mockMvc.perform(put("/users/change-password").with(csrf()).session(session)
        .contentType(MediaType.APPLICATION_JSON)
        .content(body));
  }

  // forced first change

  @Test
  @DisplayName("First login: the change works without the current password and opens the rest of the app")
  void forcedChangeOpensGate() throws Exception {
    // Arrange - logged in with the one-time password, so everything else is blocked
    saveTeacher(true);
    MockHttpSession session = loginAndGetSession();
    mockMvc.perform(get("/degrees/").session(session))
        .andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"));

    // Act
    changePassword(session, "{\"newPassword\":\"" + NEW + "\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.mustChangePassword").value(false));

    // Assert - same session, the gate is gone (whatever /degrees/ answers, it's not the gate anymore)
    int status = mockMvc.perform(get("/degrees/").session(session)).andReturn().getResponse().getStatus();
    assertNotEquals(403, status);
  }

  // normal change

  @Test
  @DisplayName("After a change only the new password logs in")
  void onlyNewPasswordWorksAfterChange() throws Exception {
    // Arrange
    saveTeacher(false);
    MockHttpSession session = loginAndGetSession();

    // Act
    changePassword(session, "{\"currentPassword\":\"" + CURRENT + "\",\"newPassword\":\"" + NEW + "\"}")
        .andExpect(status().isOk());

    // Assert
    assertEquals(401, login(CURRENT).getResponse().getStatus());
    assertEquals(200, login(NEW).getResponse().getStatus());
  }

  @Test
  @DisplayName("A wrong current password is 400 CURRENT_PASSWORD_INCORRECT, not 401")
  void wrongCurrentPassword() throws Exception {
    // Arrange
    saveTeacher(false);
    MockHttpSession session = loginAndGetSession();

    // Act + Assert - 401 would make the frontend think the session ended
    changePassword(session, "{\"currentPassword\":\"WrongPass123\",\"newPassword\":\"" + NEW + "\"}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("CURRENT_PASSWORD_INCORRECT"));
  }

  @Test
  @DisplayName("A password breaking the policy gets field codes the frontend can show")
  void policyViolationGivesFieldCodes() throws Exception {
    // Arrange
    saveTeacher(false);
    MockHttpSession session = loginAndGetSession();

    // Act + Assert - "abc1" has a letter and a digit, it's only too short
    changePassword(session, "{\"currentPassword\":\"" + CURRENT + "\",\"newPassword\":\"abc1\"}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.fieldErrors[0].field").value("newPassword"))
        .andExpect(jsonPath("$.fieldErrors[0].code").value("SIZE"))
        .andExpect(jsonPath("$.fieldErrors[0].params.min").value(8))
        .andExpect(jsonPath("$.fieldErrors[0].params.max").value(72));
  }

  @Test
  @DisplayName("Without logging in the endpoint is 401")
  void notLoggedIn() throws Exception {
    mockMvc.perform(put("/users/change-password").with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"newPassword\":\"" + NEW + "\"}"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
  }

}
