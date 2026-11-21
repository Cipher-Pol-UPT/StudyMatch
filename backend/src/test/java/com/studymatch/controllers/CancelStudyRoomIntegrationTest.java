package com.studymatch.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.test.util.ReflectionTestUtils;
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
class CancelStudyRoomIntegrationTest {

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
  private Student tutor;
  private Subject maths;
  private TutorRole mathsRole;
  private StudyRoom room;
  private MockHttpSession session;

  @BeforeEach
  void setUp() throws Exception {
    teacher = teacherRepository.save(new Teacher("it.cancel.teacher@example.com", "it.cancel.teacher@studymatch.com",
        "Test Teacher", passwordEncoder.encode(PASSWORD)));
    Category category = categoryRepository.save(new Category("IT Cancel Category"));
    maths = subjectRepository.save(new Subject("IT-CANCEL-MATH", "Test Maths", 6, category));
    tutor = saveStudent("it.cancel.tutor@example.com", "Tutor Test");
    mathsRole = saveTutorRole(tutor, maths);
    room = studyRoomRepository.save(new StudyRoom(mathsRole, tomorrowAt(14), tomorrowAt(16), 5, "test room", LINK));
    session = loginAndGetSession(tutor.getInstitutionalEmail());
  }

  private Student saveStudent(String personalEmail, String name) {
    Degree degree = degreeRepository.save(new Degree("IT Cancel Degree " + name, 3));
    Student student = new Student(personalEmail, name, passwordEncoder.encode(PASSWORD), degree, 1);
    student.setMustChangePassword(false);
    student.setPersonalityProfile(new PersonalityProfile(0.5, 0.5, 0.5, 0.5, 0.5));
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

  private static LocalDateTime tomorrowAt(int hour) {
    return LocalDateTime.now().plusDays(1).truncatedTo(ChronoUnit.DAYS).withHour(hour);
  }

  private ResultActions cancel(Long roomId, MockHttpSession session, String body) throws Exception {
    return mockMvc.perform(post("/study-rooms/" + roomId + "/cancel").with(csrf()).session(session)
        .contentType(MediaType.APPLICATION_JSON)
        .content(body));
  }

  @Test
  @DisplayName("A tutor cancels their room with a reason")
  void cancelsStudyRoom() throws Exception {
    // Act + Assert
    cancel(room.getId(), session, "{\"reason\":\"tutor is sick\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CANCELLED"));

    StudyRoom saved = studyRoomRepository.findById(room.getId()).orElseThrow();
    assertEquals("tutor is sick", saved.getCancellationReason());
  }

  @Test
  @DisplayName("Cancelling without a reason still works")
  void cancelsWithoutReason() throws Exception {
    // Act + Assert - an empty body has no reason
    cancel(room.getId(), session, "{}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CANCELLED"));
  }

  @Test
  @DisplayName("A student who doesn't host the room can't cancel it")
  void notHostCannotCancel() throws Exception {
    // Arrange
    Student other = saveStudent("it.cancel.other@example.com", "Other Tutor");
    saveTutorRole(other, maths);
    MockHttpSession otherSession = loginAndGetSession(other.getInstitutionalEmail());

    // Act + Assert
    cancel(room.getId(), otherSession, "{}")
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
  }

  @Test
  @DisplayName("A cancelled room can't be cancelled again")
  void cancelTwiceIsRejected() throws Exception {
    // Arrange
    cancel(room.getId(), session, "{}").andExpect(status().isOk());

    // Act + Assert
    cancel(room.getId(), session, "{}")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("STUDYROOM_CLOSED"));
  }

  @Test
  @DisplayName("A room that already started can't be cancelled")
  void cancelAfterStartIsRejected() throws Exception {
    // Arrange - the room started a minute ago
    ReflectionTestUtils.setField(room, "startsAt", LocalDateTime.now().minusMinutes(1));
    studyRoomRepository.saveAndFlush(room);

    // Act + Assert
    cancel(room.getId(), session, "{}")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("STUDYROOM_ALREADY_STARTED"));
  }

  @Test
  @DisplayName("A cancelled room leaves the calendar")
  void cancelledRoomLeavesCalendar() throws Exception {
    // Arrange
    cancel(room.getId(), session, "{}").andExpect(status().isOk());

    // Act + Assert
    mockMvc.perform(get("/study-rooms/").with(csrf()).session(session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.id == " + room.getId() + ")]").isEmpty());
  }

  @Test
  @DisplayName("Only students (tutors) can cancel StudyRooms")
  void teacherCannotCancel() throws Exception {
    // Arrange
    teacher.setMustChangePassword(false);
    teacherRepository.saveAndFlush(teacher);
    MockHttpSession teacherSession = loginAndGetSession(teacher.getInstitutionalEmail());

    // Act + Assert
    cancel(room.getId(), teacherSession, "{}")
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
  }

}
