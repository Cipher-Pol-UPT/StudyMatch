package com.studymatch.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.studymatch.mail.EmailSender;
import com.studymatch.models.Teacher;
import com.studymatch.repositories.TeacherRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ForgotPasswordIntegrationTest {

  private static final String EMAIL = "it.teacher@studymatch.com";
  private static final String PERSONAL_EMAIL = "it.teacher@example.com";
  private static final String OLD = "OldPass123";
  private static final String NEW = "NewPass123";

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private TeacherRepository teacherRepository;

  @Autowired
  private PasswordEncoder passwordEncoder;

  // the real app, but emails go to this fake instead of a mail server
  @MockitoBean
  private EmailSender emailSender;

  private void saveTeacher() {
    Teacher teacher = new Teacher(PERSONAL_EMAIL, EMAIL, "Test Teacher", passwordEncoder.encode(OLD));
    teacher.setMustChangePassword(false);
    teacherRepository.save(teacher);
  }

  private ResultActions forgotPassword(String email) throws Exception {
    return mockMvc.perform(post("/users/forgot-password").with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"email\":\"" + email + "\"}"));
  }

  private ResultActions resetPassword(String code, String newPassword) throws Exception {
    return mockMvc.perform(post("/users/reset-password").with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"email\":\"" + EMAIL + "\",\"code\":\"" + code + "\",\"newPassword\":\"" + newPassword + "\"}"));
  }

  private int loginStatus(String password) throws Exception {
    return mockMvc.perform(post("/users/login").with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"email\":\"" + EMAIL + "\",\"password\":\"" + password + "\"}"))
        .andReturn().getResponse().getStatus();
  }

  // the code from the "email" (the send runs in the background, so wait up to a second for it)
  private String sentCode() {
    ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
    verify(emailSender, timeout(1000)).sendPasswordResetCode(eq(PERSONAL_EMAIL), code.capture());
    return code.getValue();
  }

  @Test
  @DisplayName("Full flow without logging in: ask for a code, use it, log in with the new password")
  void fullFlow() throws Exception {
    // Arrange
    saveTeacher();

    // Act
    forgotPassword(EMAIL).andExpect(status().isNoContent());
    resetPassword(sentCode(), NEW).andExpect(status().isNoContent());

    // Assert
    assertEquals(401, loginStatus(OLD));
    assertEquals(200, loginStatus(NEW));
  }

  @Test
  @DisplayName("An unknown email gets the same 204 and no email goes out")
  void unknownEmailLooksTheSame() throws Exception {
    // Act + Assert
    forgotPassword("nobody@studymatch.com").andExpect(status().isNoContent());
    verify(emailSender, after(300).never()).sendPasswordResetCode(any(), any());
  }

  @Test
  @DisplayName("A wrong code is 400 RESET_CODE_INVALID and the old password keeps working")
  void wrongCode() throws Exception {
    // Arrange
    saveTeacher();
    forgotPassword(EMAIL);
    String wrongCode = sentCode().equals("000000") ? "111111" : "000000";

    // Act + Assert
    resetPassword(wrongCode, NEW)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("RESET_CODE_INVALID"));
    assertEquals(200, loginStatus(OLD));
  }

  @Test
  @DisplayName("A bad email format is a field error, before anything is looked up")
  void invalidEmailFormat() throws Exception {
    forgotPassword("not-an-email")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.fieldErrors[0].field").value("email"))
        .andExpect(jsonPath("$.fieldErrors[0].code").value("EMAIL"));
  }

  @Test
  @DisplayName("Public, but CSRF still applies")
  void csrfStillRequired() throws Exception {
    // no .with(csrf()) here
    mockMvc.perform(post("/users/forgot-password")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"email\":\"" + EMAIL + "\"}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("CSRF_TOKEN_INVALID"));
  }

}
