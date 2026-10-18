package com.studymatch.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

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

import com.studymatch.models.Category;
import com.studymatch.models.Degree;
import com.studymatch.models.PersonalityProfile;
import com.studymatch.models.Student;
import com.studymatch.models.StudyRoom;
import com.studymatch.models.Subject;
import com.studymatch.models.Teacher;
import com.studymatch.models.TutorApplication;
import com.studymatch.models.TutorRole;
import com.studymatch.repositories.CategoryRepository;
import com.studymatch.repositories.DegreeRepository;
import com.studymatch.repositories.StudentRepository;
import com.studymatch.repositories.StudyRoomRepository;
import com.studymatch.repositories.SubjectRepository;
import com.studymatch.repositories.TeacherRepository;
import com.studymatch.repositories.TutorApplicationRepository;
import com.studymatch.repositories.TutorRoleRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class StudyRoomIntegrationTest {

  private static final String PASSWORD = "Test1234";
  private static final String LINK = "https://meet.google.com/abc-defg-hij";

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
  private CategoryRepository categoryRepository;

  @Autowired
  private SubjectRepository subjectRepository;

  @Autowired
  private TutorApplicationRepository tutorApplicationRepository;

  @Autowired
  private TutorRoleRepository tutorRoleRepository;

  @Autowired
  private StudyRoomRepository studyRoomRepository;

  private Teacher teacher;
  private Category category;
  private Student tutor;
  private Subject maths;
  private Subject physics;
  private TutorRole mathsRole;
  private TutorRole physicsRole;
  private MockHttpSession session;

  // a tutor of two subjects, logged in (everything is rolled back after each test)
  @BeforeEach
  void setUp() throws Exception {
    teacher = teacherRepository.save(new Teacher("it.sr.teacher@example.com", "it.sr.teacher@studymatch.com",
        "Test Teacher", passwordEncoder.encode(PASSWORD)));
    category = categoryRepository.save(new Category("IT StudyRoom Category"));
    maths = subjectRepository.save(new Subject("IT-SR-MATH", "Test Maths", 6, category));
    physics = subjectRepository.save(new Subject("IT-SR-PHYS", "Test Physics", 6, category));
    tutor = saveStudent("it.sr.tutor@example.com", "Tutor Test");
    mathsRole = saveTutorRole(tutor, maths);
    physicsRole = saveTutorRole(tutor, physics);
    session = loginAndGetSession(tutor.getInstitutionalEmail());
  }

  private Student saveStudent(String personalEmail, String name) {
    Degree degree = degreeRepository.save(new Degree("IT StudyRoom Degree " + name));
    Student student = new Student(personalEmail, name, passwordEncoder.encode(PASSWORD), degree, 1);
    student.setMustChangePassword(false);
    student.setPersonalityProfile(new PersonalityProfile(0.5, 0.5, 0.5, 0.5));
    student = studentRepository.save(student);
    student.generateInstitutionalEmail("studymatch.com");
    return studentRepository.saveAndFlush(student);
  }

  private TutorRole saveTutorRole(Student student, Subject subject) {
    TutorApplication application = tutorApplicationRepository
        .save(new TutorApplication(student, subject, "test motivation"));
    return tutorRoleRepository.save(application.approve(teacher));
  }

  private MockHttpSession loginAndGetSession(String email) throws Exception {
    MvcResult result = mockMvc.perform(post("/users/login").with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
        .andReturn();
    assertEquals(200, result.getResponse().getStatus());
    return (MockHttpSession) result.getRequest().getSession(false);
  }

  // tomorrow at 14:00, so tests never depend on the time they run at
  private static LocalDateTime tomorrowAt(int hour) {
    return LocalDateTime.now().plusDays(1).truncatedTo(ChronoUnit.DAYS).withHour(hour);
  }

  private static String body(Long subjectId, LocalDateTime startsAt, LocalDateTime endsAt, Integer capacity,
      String link) {
    return """
        {"subjectId": %s, "startsAt": %s, "endsAt": %s, "description": "test room", "capacity": %s, "link": %s}"""
        .formatted(subjectId, quote(startsAt), quote(endsAt), capacity, quote(link));
  }

  private static String quote(Object value) {
    return value == null ? "null" : "\"" + value + "\"";
  }

  private ResultActions create(String body) throws Exception {
    return mockMvc.perform(post("/study-rooms/").with(csrf()).session(session)
        .contentType(MediaType.APPLICATION_JSON)
        .content(body));
  }

  private StudyRoom saveRoom(TutorRole role, LocalDateTime startsAt, LocalDateTime endsAt) {
    return studyRoomRepository.save(new StudyRoom(role, startsAt, endsAt, 5, "existing room", LINK));
  }

  // create (#49)

  @Test
  @DisplayName("A tutor creates a StudyRoom and it starts as SCHEDULED")
  void createsStudyRoom() throws Exception {
    // Act
    create(body(maths.getId(), tomorrowAt(14), tomorrowAt(16), 5, LINK))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").isNumber())
        .andExpect(jsonPath("$.subjectName").value("Test Maths"))
        .andExpect(jsonPath("$.tutorNumber").value(tutor.getNumber()))
        .andExpect(jsonPath("$.capacity").value(5))
        .andExpect(jsonPath("$.link").value(LINK))
        .andExpect(jsonPath("$.status").value("SCHEDULED"))
        .andExpect(jsonPath("$.participantCount").value(0));

    // Assert
    assertEquals(1, studyRoomRepository.findOverlapping(tutor.getNumber(), tomorrowAt(14), tomorrowAt(16)).size());
  }

  @Test
  @DisplayName("Missing required fields are reported one by one")
  void missingFields() throws Exception {
    // Act + Assert
    create(body(null, null, null, null, null))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.fieldErrors.length()").value(5));
  }

  @Test
  @DisplayName("A capacity over 20 is rejected with the maximum")
  void capacityOverTheMaximum() throws Exception {
    // Act + Assert
    create(body(maths.getId(), tomorrowAt(14), tomorrowAt(16), 21, LINK))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors[0].field").value("capacity"))
        .andExpect(jsonPath("$.fieldErrors[0].code").value("MAX"))
        .andExpect(jsonPath("$.fieldErrors[0].params.value").value(20));
  }

  @Test
  @DisplayName("Only Google Meet links are accepted")
  void notAMeetLink() throws Exception {
    // Act + Assert
    create(body(maths.getId(), tomorrowAt(14), tomorrowAt(16), 5, "https://zoom.us/j/123"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors[0].field").value("link"))
        .andExpect(jsonPath("$.fieldErrors[0].code").value("PATTERN"));
  }

  @Test
  @DisplayName("A StudyRoom in the past is rejected")
  void inThePast() throws Exception {
    // Arrange
    LocalDateTime yesterday = tomorrowAt(14).minusDays(2);

    // Act + Assert
    create(body(maths.getId(), yesterday, yesterday.plusHours(2), 5, LINK))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("STUDYROOM_IN_PAST"));
  }

  @Test
  @DisplayName("A StudyRoom that ends before it starts is rejected")
  void endsBeforeStart() throws Exception {
    // Act + Assert
    create(body(maths.getId(), tomorrowAt(16), tomorrowAt(14), 5, LINK))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("STUDYROOM_ENDS_BEFORE_START"));
  }

  @Test
  @DisplayName("Overlapping another room of the tutor (even in another subject) names the conflicting room")
  void overlapsAnotherRoom() throws Exception {
    // Arrange
    StudyRoom existing = saveRoom(physicsRole, tomorrowAt(15), tomorrowAt(17));

    // Act + Assert
    create(body(maths.getId(), tomorrowAt(14), tomorrowAt(16), 5, LINK))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("STUDYROOM_OVERLAP"))
        .andExpect(jsonPath("$.params.id").value(existing.getId()))
        .andExpect(jsonPath("$.params.subject").value("Test Physics"))
        .andExpect(jsonPath("$.params.startsAt").value(tomorrowAt(15).toString()));
  }

  @Test
  @DisplayName("Back-to-back rooms don't overlap")
  void backToBack() throws Exception {
    // Arrange
    saveRoom(mathsRole, tomorrowAt(12), tomorrowAt(14));

    // Act + Assert
    create(body(maths.getId(), tomorrowAt(14), tomorrowAt(16), 5, LINK))
        .andExpect(status().isCreated());
  }

  @Test
  @DisplayName("A cancelled room doesn't block its time slot")
  void cancelledRoomDoesNotOverlap() throws Exception {
    // Arrange
    StudyRoom cancelled = saveRoom(mathsRole, tomorrowAt(14), tomorrowAt(16));
    cancelled.cancel("test reason");
    studyRoomRepository.saveAndFlush(cancelled);

    // Act + Assert
    create(body(maths.getId(), tomorrowAt(14), tomorrowAt(16), 5, LINK))
        .andExpect(status().isCreated());
  }

  @Test
  @DisplayName("Another tutor's room at the same time doesn't conflict")
  void otherTutorsRoom() throws Exception {
    // Arrange
    Student other = saveStudent("it.sr.other@example.com", "Other Tutor");
    saveRoom(saveTutorRole(other, maths), tomorrowAt(14), tomorrowAt(16));

    // Act + Assert
    create(body(maths.getId(), tomorrowAt(14), tomorrowAt(16), 5, LINK))
        .andExpect(status().isCreated());
  }

  @Test
  @DisplayName("A student can't create a room for a subject they don't tutor")
  void notTutorOfSubject() throws Exception {
    // Arrange
    Subject chemistry = subjectRepository.save(new Subject("IT-SR-CHEM", "Test Chemistry", 6, category));

    // Act + Assert
    create(body(chemistry.getId(), tomorrowAt(14), tomorrowAt(16), 5, LINK))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
  }

  @Test
  @DisplayName("Only students (tutors) can create StudyRooms")
  void teacherCannotCreate() throws Exception {
    // Arrange
    teacher.setMustChangePassword(false);
    teacherRepository.saveAndFlush(teacher);
    session = loginAndGetSession(teacher.getInstitutionalEmail());

    // Act + Assert
    create(body(maths.getId(), tomorrowAt(14), tomorrowAt(16), 5, LINK))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
  }

}
