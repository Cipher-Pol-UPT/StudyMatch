package com.studymatch.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Set;

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

import com.studymatch.enums.Achievement;
import com.studymatch.enums.Language;
import com.studymatch.models.Degree;
import com.studymatch.models.EarnedAchievement;
import com.studymatch.models.PersonalityProfile;
import com.studymatch.models.Student;
import com.studymatch.models.Teacher;
import com.studymatch.repositories.DegreeRepository;
import com.studymatch.repositories.EarnedAchievementRepository;
import com.studymatch.repositories.StudentRepository;
import com.studymatch.repositories.TeacherRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ViewAchievementsIntegrationTest {

  private static final String PASSWORD = "Test1234";
  private static final String MY_ACHIEVEMENTS = "/achievements/me";

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

  @Autowired
  private EarnedAchievementRepository earnedAchievementRepository;

  private Student student;

  @BeforeEach
  void setUp() {
    Degree degree = degreeRepository.save(new Degree("Integration Test Degree", 3));
    student = new Student("student.it@example.com", "Test Student", passwordEncoder.encode(PASSWORD), degree, 1);
    student.setMustChangePassword(false);
    student.setPersonalityProfile(new PersonalityProfile(0.5, 0.5, 0.5, 0.5));
    student.setLanguages(Set.of(Language.PORTUGUESE));
    student = studentRepository.save(student);
    student.generateInstitutionalEmail("studymatch.com");
  }

  @Test
  @DisplayName("A student gets every achievement, with the date and code on the one they earned")
  void studentGetsEveryAchievement() throws Exception {
    // Arrange
    EarnedAchievement firstRoom = earnedAchievementRepository
        .save(new EarnedAchievement(student, Achievement.FIRST_STUDYROOM));
    MockHttpSession session = loginAs(student.getInstitutionalEmail());

    // Act + Assert
    mockMvc.perform(get(MY_ACHIEVEMENTS).session(session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(Achievement.values().length))
        .andExpect(jsonPath("$[0].achievement").value("FIRST_STUDYROOM"))
        .andExpect(jsonPath("$[0].earned").value(true))
        .andExpect(jsonPath("$[0].earnedAt").exists())
        .andExpect(jsonPath("$[0].verificationCode").value(firstRoom.getVerificationCode()))
        .andExpect(jsonPath("$[1].achievement").value("DEDICATED_TUTOR"))
        .andExpect(jsonPath("$[1].earned").value(false))
        .andExpect(jsonPath("$[1].verificationCode").doesNotExist());
  }

  @Test
  @DisplayName("A student with no achievements gets all of them, none earned")
  void studentWithNoneGetsAllNotEarned() throws Exception {
    // Arrange
    MockHttpSession session = loginAs(student.getInstitutionalEmail());

    // Act + Assert
    mockMvc.perform(get(MY_ACHIEVEMENTS).session(session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(Achievement.values().length))
        .andExpect(jsonPath("$[?(@.earned == true)]").isEmpty());
  }

  @Test
  @DisplayName("A teacher gets 403 ACCESS_DENIED")
  void teacherGetsAccessDenied() throws Exception {
    // Arrange
    Teacher teacher = new Teacher("teacher.it@example.com", "teacher.it@studymatch.com", "Test Teacher",
        passwordEncoder.encode(PASSWORD));
    teacher.setMustChangePassword(false);
    teacherRepository.save(teacher);
    MockHttpSession session = loginAs(teacher.getInstitutionalEmail());

    // Act + Assert
    mockMvc.perform(get(MY_ACHIEVEMENTS).session(session))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
  }

  @Test
  @DisplayName("Without a session gets 401 UNAUTHENTICATED")
  void noSessionGetsUnauthenticated() throws Exception {
    // Act + Assert
    mockMvc.perform(get(MY_ACHIEVEMENTS))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
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
