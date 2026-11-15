package com.studymatch.config;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiDocsIntegrationTest {

  private static final String GRADE_STUDENT =
      "$.paths['/subject-editions/{id}/assignments/{assignmentId}/grades/{studentNumber}'].put";

  @Autowired
  private MockMvc mockMvc;

  @Test
  @DisplayName("The API docs open without logging in")
  void apiDocsArePublic() throws Exception {
    // Act + Assert
    mockMvc.perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.info.title").value("StudyMatch API"));
  }

  @Test
  @DisplayName("Swagger UI opens without logging in")
  void swaggerUiIsPublic() throws Exception {
    // Act + Assert
    mockMvc.perform(get("/swagger-ui/index.html"))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("A real endpoint shows its summary and its own codes next to the common ones")
  void endpointShowsItsCodes() throws Exception {
    // Act + Assert
    mockMvc.perform(get("/v3/api-docs"))
        .andExpect(jsonPath(GRADE_STUDENT + ".summary").value(containsString("final grade")))
        .andExpect(jsonPath(GRADE_STUDENT + ".responses['400'].description")
            .value("`VALIDATION_FAILED`, `STUDENT_NOT_ENROLLED`"))
        .andExpect(jsonPath(GRADE_STUDENT + ".responses['404'].description").value("`NOT_FOUND`"))
        .andExpect(jsonPath(GRADE_STUDENT + ".responses['403'].description")
            .value("`ACCESS_DENIED`, `CSRF_TOKEN_INVALID`"));
  }

  @Test
  @DisplayName("Opening the docs didn't open anything else: a normal endpoint still needs a login")
  void otherEndpointsStillNeedLogin() throws Exception {
    // Act + Assert
    mockMvc.perform(get("/degrees/"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
  }

}
