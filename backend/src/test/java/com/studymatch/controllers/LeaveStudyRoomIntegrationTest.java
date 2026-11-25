package com.studymatch.controllers;

import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.studymatch.enums.NotificationTarget;
import com.studymatch.enums.NotificationType;
import com.studymatch.models.Category;
import com.studymatch.models.Degree;
import com.studymatch.models.Notification;
import com.studymatch.models.PersonalityProfile;
import com.studymatch.models.Student;
import com.studymatch.models.StudyRoom;
import com.studymatch.models.Subject;
import com.studymatch.models.Teacher;
import com.studymatch.models.TutorApplication;
import com.studymatch.models.TutorRole;
import com.studymatch.models.User;
import com.studymatch.repositories.CategoryRepository;
import com.studymatch.repositories.DegreeRepository;
import com.studymatch.repositories.NotificationRepository;
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
class LeaveStudyRoomIntegrationTest {

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
  @Autowired
  private NotificationRepository notificationRepository;

  private Teacher teacher;
  private Category category;
  private Student tutor;
  private Student participant;
  private Student otherStudent;
  private Subject maths;
  private TutorRole mathsRole;
  private StudyRoom room;
  private MockHttpSession session;

  // a tutor, a participant who joined a future room, and another student (everything is rolled back after each test)
  @BeforeEach
  void setUp() throws Exception {
    teacher = teacherRepository.save(new Teacher("it.leave.teacher@example.com", "it.leave.teacher@studymatch.com",
        "Test Teacher", passwordEncoder.encode(PASSWORD)));
    category = categoryRepository.save(new Category("IT Leave Category"));
    maths = subjectRepository.save(new Subject("IT-LEAVE-MATH", "Test Maths", 6, category));

    tutor = saveStudent("it.leave.tutor@example.com", "Tutor Test");
    participant = saveStudent("it.leave.participant@example.com", "Participant Test");
    otherStudent = saveStudent("it.leave.other@example.com", "Other Student");

    mathsRole = saveTutorRole(tutor, maths);

    room = new StudyRoom(mathsRole, tomorrowAt(14), tomorrowAt(16), 5, "test room", LINK);
    room.join(participant);
    room = studyRoomRepository.save(room);

    session = loginAndGetSession(participant.getInstitutionalEmail());
  }

  private Student saveStudent(String personalEmail, String name) {
    Degree degree = degreeRepository.save(new Degree("IT Leave Degree " + name, 3));
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

  private ResultActions leave(Long roomId, MockHttpSession session) throws Exception {
    return mockMvc.perform(post("/study-rooms/" + roomId + "/leave").with(csrf()).session(session));
  }

  private static LocalDateTime tomorrowAt(int hour) {
    return LocalDateTime.now().plusDays(1).truncatedTo(ChronoUnit.DAYS).withHour(hour);
  }

  @Test
  @DisplayName("A participant leaves and their place is freed")
  void participantLeaves() throws Exception {
    // Act + Assert
    leave(room.getId(), session)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.participantCount").value(0))
        .andExpect(jsonPath("$.status").value("SCHEDULED"))
        .andExpect(jsonPath("$.link").value(nullValue()));

    assertEquals(0, studyRoomRepository.findById(room.getId()).orElseThrow().getParticipations().size());
  }

  @Test
  @DisplayName("Leaving a full room makes it scheduled again")
  void leavingFullRoomMakesItScheduled() throws Exception {
    // Arrange
    StudyRoom fullRoom = new StudyRoom(mathsRole, tomorrowAt(14), tomorrowAt(16), 2, "full room", LINK);
    fullRoom.join(participant);
    fullRoom.join(otherStudent);
    fullRoom = studyRoomRepository.save(fullRoom);

    // Act + Assert
    leave(fullRoom.getId(), session)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.participantCount").value(1))
        .andExpect(jsonPath("$.status").value("SCHEDULED"));
  }

  @Test
  @DisplayName("A student who never joined can't leave")
  void nonParticipantCannotLeave() throws Exception {
    // Arrange
    MockHttpSession otherSession = loginAndGetSession(otherStudent.getInstitutionalEmail());

    // Act + Assert
    leave(room.getId(), otherSession)
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("NOT_A_PARTICIPANT"));
  }

  @Test
  @DisplayName("A participant can't leave after the room started")
  void cannotLeaveAfterStart() throws Exception {
    // Arrange - the room started a minute ago
    ReflectionTestUtils.setField(room, "startsAt", LocalDateTime.now().minusMinutes(1));
    studyRoomRepository.saveAndFlush(room);

    // Act + Assert
    leave(room.getId(), session)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("STUDYROOM_ALREADY_STARTED"));
  }

  @Test
  @DisplayName("A room that doesn't exist is 404")
  void unknownRoom() throws Exception {
    leave(9_999_999L, session).andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("Teachers and unauthenticated users cannot leave")
  void onlyStudentsCanLeave() throws Exception {
    teacher.setMustChangePassword(false);
    teacherRepository.saveAndFlush(teacher);

    mockMvc.perform(post("/study-rooms/" + room.getId() + "/leave").with(csrf())
        .session(loginAndGetSession(teacher.getInstitutionalEmail())))
        .andExpect(status().isForbidden());
    mockMvc.perform(post("/study-rooms/" + room.getId() + "/leave").with(csrf()))
        .andExpect(status().isUnauthorized());
  }

  private List<Notification> notificationsOf(User user) {
    return notificationRepository.findByRecipientNumber(user.getNumber(), Pageable.unpaged()).getContent();
  }

  @Test
  @DisplayName("Leaving tells the tutor who left, pointing at the room")
  void leavingNotifiesTutor() throws Exception {
    // Act
    leave(room.getId(), session).andExpect(status().isOk());

    // Assert
    List<Notification> notifications = notificationsOf(tutor);
    assertEquals(1, notifications.size());
    Notification notification = notifications.getFirst();
    assertEquals(NotificationType.STUDYROOM_LEFT, notification.getType());
    assertEquals(NotificationTarget.STUDYROOM, notification.getTarget());
    assertEquals(room.getId(), notification.getTargetId());
    assertEquals("Test Maths", notification.getParams().get("subject"));
    assertEquals("Participant Test", notification.getParams().get("student"));
  }

  @Test
  @DisplayName("A leave that fails tells nobody")
  void failedLeaveNotifiesNobody() throws Exception {
    // Act
    leave(room.getId(), loginAndGetSession(otherStudent.getInstitutionalEmail())).andExpect(status().isForbidden());

    // Assert
    assertTrue(notificationsOf(tutor).isEmpty());
  }

}
