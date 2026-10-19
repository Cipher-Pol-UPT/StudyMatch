package com.studymatch.demo;

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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import com.studymatch.repositories.AbsenceRepository;
import com.studymatch.repositories.AcademicYearRepository;
import com.studymatch.repositories.CategoryRepository;
import com.studymatch.repositories.DegreeRepository;
import com.studymatch.repositories.EarnedAchievementRepository;
import com.studymatch.repositories.EnrolmentRepository;
import com.studymatch.repositories.LessonRepository;
import com.studymatch.repositories.SubjectEditionRepository;
import com.studymatch.repositories.SubjectRepository;
import com.studymatch.repositories.UserRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class DemoDataIntegrationTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private UserRepository userRepository;

  @Autowired
  private DegreeRepository degreeRepository;

  @Autowired
  private CategoryRepository categoryRepository;

  @Autowired
  private SubjectRepository subjectRepository;

  @Autowired
  private AcademicYearRepository academicYearRepository;

  @Autowired
  private SubjectEditionRepository subjectEditionRepository;

  @Autowired
  private LessonRepository lessonRepository;

  @Autowired
  private EnrolmentRepository enrolmentRepository;

  @Autowired
  private AbsenceRepository absenceRepository;

  @Autowired
  private EarnedAchievementRepository earnedAchievementRepository;

  @Autowired
  private PasswordEncoder passwordEncoder;

  @Value("${studymatch.institution-domain}")
  private String institutionDomain;

  private DemoData demoData;

  @BeforeEach
  void setUp() {
    demoData = new DemoData(userRepository, degreeRepository, categoryRepository, subjectRepository,
        academicYearRepository, subjectEditionRepository, lessonRepository, enrolmentRepository, absenceRepository,
        earnedAchievementRepository, passwordEncoder, institutionDomain);
  }

  @Test
  @DisplayName("The demo student with history logs in straight to a profile with attendance")
  void studentWithHistoryHasAProfile() throws Exception {
    // Arrange
    demoData.run(null);
    String email = userRepository.findAll().stream()
        .filter(user -> user.getName().equals("Maria Costa")).findFirst().orElseThrow().getInstitutionalEmail();

    // Act
    MockHttpSession session = loginAs(email);

    // Assert
    mockMvc.perform(get("/students/me/profile").session(session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.privateProfile.degree").value("Engenharia Informática"))
        .andExpect(jsonPath("$.privateProfile.hasAcademicHistory").value(true))
        .andExpect(jsonPath("$.privateProfile.attendance").value(93.75))
        .andExpect(jsonPath("$.achievements[0].achievement").value("FIRST_STUDYROOM"));
  }

  @Test
  @DisplayName("The cold start student has to change the password and take the personality test")
  void coldStartStudentGoesThroughTheGates() throws Exception {
    // Arrange
    demoData.run(null);
    String email = userRepository.findAll().stream()
        .filter(user -> user.getName().equals("Tiago Santos")).findFirst().orElseThrow().getInstitutionalEmail();

    // Act
    MockHttpSession session = loginAs(email);

    // Assert
    mockMvc.perform(get("/users/me").session(session))
        .andExpect(jsonPath("$.mustChangePassword").value(true))
        .andExpect(jsonPath("$.personalityTestRequired").value(true));
  }

  @Test
  @DisplayName("The admin, staff and teacher accounts can log in")
  void employeesCanLogIn() throws Exception {
    // Arrange
    demoData.run(null);

    // Act + Assert
    for (String username : new String[] { "admin", "ana.lima", "joao.silva" }) {
      MockHttpSession session = loginAs(username + "@" + institutionDomain);
      mockMvc.perform(get("/users/me").session(session)).andExpect(jsonPath("$.mustChangePassword").value(false));
    }
  }

  @Test
  @DisplayName("Running it on a database that already has users changes nothing")
  void doesNothingWhenThereAreUsers() {
    // Arrange
    demoData.run(null);
    long users = userRepository.count();

    // Act
    demoData.run(null);

    // Assert
    assertEquals(users, userRepository.count());
    assertEquals(3, degreeRepository.count());
  }

  private MockHttpSession loginAs(String email) throws Exception {
    MvcResult result = mockMvc.perform(post("/users/login").with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"email\":\"" + email + "\",\"password\":\"" + DemoData.PASSWORD + "\"}"))
        .andReturn();
    assertEquals(200, result.getResponse().getStatus());
    return (MockHttpSession) result.getRequest().getSession(false);
  }

}
