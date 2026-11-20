package com.studymatch.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.studymatch.enums.Achievement;
import com.studymatch.models.AchievementRecord;
import com.studymatch.models.Degree;
import com.studymatch.models.EarnedAchievement;
import com.studymatch.models.PersonalityProfile;
import com.studymatch.models.Student;
import com.studymatch.models.Teacher;
import com.studymatch.repositories.AchievementRecordRepository;
import com.studymatch.repositories.DegreeRepository;
import com.studymatch.repositories.EarnedAchievementRepository;
import com.studymatch.repositories.StudentRepository;
import com.studymatch.repositories.TeacherRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ExportCertificateIntegrationTest {

  private static final String PASSWORD = "Test1234";

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

  @Autowired
  private AchievementRecordRepository achievementRecordRepository;

  private Student tutor;
  private EarnedAchievement firstRoom;
  private EarnedAchievement mentor;
  private MockHttpSession session;

  // a tutor with two achievements, logged in (rolled back after each test)
  @BeforeEach
  void setUp() throws Exception {
    Degree degree = degreeRepository.save(new Degree("IT Certificate Degree", 3));
    Student student = new Student("it.cert.tutor@example.com", "Tutor Test", passwordEncoder.encode(PASSWORD), degree, 2);
    student.setMustChangePassword(false);
    student.setPersonalityProfile(new PersonalityProfile(0.5, 0.5, 0.5, 0.5, 0.5));
    student = studentRepository.save(student);
    student.generateInstitutionalEmail("studymatch.com");
    tutor = studentRepository.saveAndFlush(student);
    firstRoom = earnedAchievementRepository.save(new EarnedAchievement(tutor, Achievement.FIRST_STUDYROOM));
    mentor = earnedAchievementRepository.save(new EarnedAchievement(tutor, Achievement.MENTOR));
    session = login(tutor.getInstitutionalEmail());
  }

  private MockHttpSession login(String email) throws Exception {
    MvcResult result = mockMvc.perform(post("/users/login").with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
        .andReturn();
    assertEquals(200, result.getResponse().getStatus());
    return (MockHttpSession) result.getRequest().getSession(false);
  }

  private ResultActions export(MockHttpSession as, String body) throws Exception {
    return mockMvc.perform(post("/achievements/certificate").session(as).with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content(body));
  }

  private static boolean isPdf(byte[] content) {
    return new String(content, 0, 5, StandardCharsets.US_ASCII).equals("%PDF-");
  }

  @Test
  @DisplayName("One achievement downloads its certificate and saves the figures it shows")
  void oneAchievement() throws Exception {
    // Act
    byte[] pdf = export(session, "{\"achievements\":[\"MENTOR\"],\"language\":\"en\"}")
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_PDF))
        .andExpect(header().string("Content-Disposition", "attachment; filename=\"certificate.pdf\""))
        .andReturn().getResponse().getContentAsByteArray();

    // Assert
    assertTrue(isPdf(pdf));
    EarnedAchievement saved = earnedAchievementRepository.findById(mentor.getId()).orElseThrow();
    assertNotNull(saved.getEvidence());
    assertEquals(0L, saved.getEvidence().roomsHeld());
    assertEquals(LocalDate.now(), saved.getEvidence().recordedAt());
    assertNull(earnedAchievementRepository.findById(firstRoom.getId()).orElseThrow().getEvidence());
    assertEquals(0, achievementRecordRepository.count());
  }

  @Test
  @DisplayName("Several achievements download one record, with its own code")
  void severalAchievements() throws Exception {
    // Act
    byte[] pdf = export(session, "{\"achievements\":[\"MENTOR\",\"FIRST_STUDYROOM\"],\"language\":\"pt\"}")
        .andExpect(status().isOk())
        .andExpect(header().string("Content-Disposition", "attachment; filename=\"achievements.pdf\""))
        .andReturn().getResponse().getContentAsByteArray();

    // Assert
    assertTrue(isPdf(pdf));
    List<AchievementRecord> records = achievementRecordRepository.findAll();
    assertEquals(1, records.size());
    assertEquals(List.of(Achievement.FIRST_STUDYROOM, Achievement.MENTOR),
        records.get(0).getAchievements().stream().map(EarnedAchievement::getAchievement).toList());
  }

  @Test
  @DisplayName("Choosing no achievement is refused")
  void noAchievement() throws Exception {
    // Act + Assert
    export(session, "{\"achievements\":[],\"language\":\"en\"}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors[0].field").value("achievements"))
        .andExpect(jsonPath("$.fieldErrors[0].code").value("NOT_EMPTY"));
  }

  @Test
  @DisplayName("A language other than en or pt is refused")
  void unknownLanguage() throws Exception {
    // Act + Assert
    export(session, "{\"achievements\":[\"MENTOR\"],\"language\":\"fr\"}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors[0].field").value("language"));
  }

  @Test
  @DisplayName("An achievement the student doesn't have gives ACHIEVEMENT_NOT_EARNED and nothing is saved")
  void notEarned() throws Exception {
    // Act + Assert
    export(session, "{\"achievements\":[\"MENTOR\",\"TOP_RATED\"],\"language\":\"en\"}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("ACHIEVEMENT_NOT_EARNED"))
        .andExpect(jsonPath("$.params.achievement").value("TOP_RATED"));
    assertNull(earnedAchievementRepository.findById(mentor.getId()).orElseThrow().getEvidence());
    assertEquals(0, achievementRecordRepository.count());
  }

  @Test
  @DisplayName("Teachers can't export certificates")
  void teacherCantExport() throws Exception {
    // Arrange
    Teacher teacher = new Teacher("it.cert.teacher@example.com", "it.cert.teacher@studymatch.com", "Teacher Test",
        passwordEncoder.encode(PASSWORD));
    teacher.setMustChangePassword(false);
    teacherRepository.saveAndFlush(teacher);

    // Act + Assert
    export(login("it.cert.teacher@studymatch.com"), "{\"achievements\":[\"MENTOR\"],\"language\":\"en\"}")
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
  }

  @Test
  @DisplayName("Anyone can verify a certificate's code without logging in")
  void verifyCertificate() throws Exception {
    // Arrange
    export(session, "{\"achievements\":[\"MENTOR\"],\"language\":\"en\"}").andExpect(status().isOk());

    // Act + Assert
    mockMvc.perform(get("/achievements/certificates/" + mentor.getVerificationCode()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.record").value(false))
        .andExpect(jsonPath("$.studentName").value("Tutor Test"))
        .andExpect(jsonPath("$.studentNumber").value(tutor.getNumber()))
        .andExpect(jsonPath("$.degree").value("IT Certificate Degree"))
        .andExpect(jsonPath("$.issuedAt").value(LocalDate.now().toString()))
        .andExpect(jsonPath("$.achievements.length()").value(1))
        .andExpect(jsonPath("$.achievements[0].achievement").value("MENTOR"))
        .andExpect(jsonPath("$.achievements[0].evidence.studentsTutored").value(0));
  }

  @Test
  @DisplayName("Anyone can verify a record's code without logging in")
  void verifyRecord() throws Exception {
    // Arrange
    export(session, "{\"achievements\":[\"MENTOR\",\"FIRST_STUDYROOM\"],\"language\":\"en\"}").andExpect(status().isOk());
    String code = achievementRecordRepository.findAll().get(0).getVerificationCode();

    // Act + Assert
    mockMvc.perform(get("/achievements/certificates/" + code))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.record").value(true))
        .andExpect(jsonPath("$.achievements.length()").value(2))
        .andExpect(jsonPath("$.achievements[0].achievement").value("FIRST_STUDYROOM"));
  }

  @Test
  @DisplayName("An unknown code gives NOT_FOUND")
  void unknownCode() throws Exception {
    // Act + Assert
    mockMvc.perform(get("/achievements/certificates/not-a-code"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
  }

}
