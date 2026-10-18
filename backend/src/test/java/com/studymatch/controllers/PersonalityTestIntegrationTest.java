package com.studymatch.controllers;

import static org.hamcrest.Matchers.closeTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.studymatch.models.Degree;
import com.studymatch.models.Student;
import com.studymatch.models.Teacher;
import com.studymatch.repositories.DegreeRepository;
import com.studymatch.repositories.StudentRepository;
import com.studymatch.repositories.TeacherRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PersonalityTestIntegrationTest {

  private static final String PASSWORD = "Test1234";
  private static final double DELTA = 1e-4;
  // the worked example from the spec
  private static final String ANSWERS = "{\"answers\":[4,5,2,4,5,2,2,4,3,1,4,4,3,4,5]}";

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private PasswordEncoder passwordEncoder;

  @Autowired
  private DegreeRepository degreeRepository;

  @Autowired
  private StudentRepository studentRepository;

  @Autowired
  private TeacherRepository teacherRepository;

  private Student student;

  @BeforeEach
  void setUp() {
    Degree degree = degreeRepository.save(new Degree("Integration Test Degree"));
    student = new Student("student.it@example.com", "Test Student", passwordEncoder.encode(PASSWORD), degree, 1);
    student.setMustChangePassword(false);
    student = studentRepository.save(student);
    student.generateInstitutionalEmail("studymatch.com");
  }

  @Test
  @DisplayName("Submitting the test saves the profile and opens the gate")
  void submittingTheTestOpensTheGate() throws Exception {
    // Arrange
    MockHttpSession session = loginAs(student.getInstitutionalEmail());
    mockMvc.perform(get("/users/me").session(session))
        .andExpect(jsonPath("$.personalityTestRequired").value(true));
    mockMvc.perform(get("/degrees/").session(session))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("PERSONALITY_TEST_REQUIRED"));

    // Act
    submitTest(session)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.leadership").value(closeTo(0.6417, DELTA), Double.class))
        .andExpect(jsonPath("$.problemSolving").value(closeTo(0.6333, DELTA), Double.class))
        .andExpect(jsonPath("$.creativity").value(closeTo(0.9375, DELTA), Double.class))
        .andExpect(jsonPath("$.execution").value(closeTo(0.4333, DELTA), Double.class));

    // Assert - same session, no new login needed
    mockMvc.perform(get("/users/me").session(session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.personalityTestRequired").value(false));
    // past the gate now; 404 only because /degrees/ has no endpoints yet
    mockMvc.perform(get("/degrees/").session(session))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
  }

  @Test
  @DisplayName("Submitting the test a second time gets PERSONALITY_TEST_ALREADY_TAKEN")
  void secondSubmitIsRejected() throws Exception {
    // Arrange
    MockHttpSession session = loginAs(student.getInstitutionalEmail());
    submitTest(session).andExpect(status().isOk());

    // Act + Assert
    mockMvc.perform(post("/students/personality-test").with(csrf()).session(session)
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"answers\":[1,1,1,1,1,1,1,1,1,1,1,1,1,1,1]}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("PERSONALITY_TEST_ALREADY_TAKEN"));
  }

  @Test
  @DisplayName("Invalid answers get VALIDATION_FAILED and the gate stays closed")
  void invalidAnswersAreRejected() throws Exception {
    // Arrange
    MockHttpSession session = loginAs(student.getInstitutionalEmail());

    // Act - 14 answers
    mockMvc.perform(post("/students/personality-test").with(csrf()).session(session)
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"answers\":[3,3,3,3,3,3,3,3,3,3,3,3,3,3]}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

    // Assert
    mockMvc.perform(get("/users/me").session(session))
        .andExpect(jsonPath("$.personalityTestRequired").value(true));
  }

  @Test
  @DisplayName("A teacher can't take the personality test")
  void teacherGetsAccessDenied() throws Exception {
    // Arrange
    Teacher teacher = new Teacher("teacher.it@example.com", "teacher.it@studymatch.com", "Test Teacher",
        passwordEncoder.encode(PASSWORD));
    teacher.setMustChangePassword(false);
    teacherRepository.save(teacher);
    MockHttpSession session = loginAs("teacher.it@studymatch.com");

    // Act + Assert
    submitTest(session)
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
  }

  private ResultActions submitTest(MockHttpSession session) throws Exception {
    return mockMvc.perform(post("/students/personality-test").with(csrf()).session(session)
        .contentType(MediaType.APPLICATION_JSON)
        .content(ANSWERS));
  }

  private MockHttpSession loginAs(String email) throws Exception {
    MvcResult result = mockMvc.perform(post("/users/login").with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
        .andReturn();
    assertEquals(200, result.getResponse().getStatus());
    return (MockHttpSession) result.getRequest().getSession(false);
  }

}
