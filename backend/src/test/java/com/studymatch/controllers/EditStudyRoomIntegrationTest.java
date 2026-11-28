package com.studymatch.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.studymatch.enums.NotificationTarget;
import com.studymatch.enums.NotificationType;
import com.studymatch.enums.StudyRoomStatus;
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
import com.studymatch.utils.MeetLinkGenerator;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class EditStudyRoomIntegrationTest {

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

  // never Google in tests
  @MockitoBean
  private MeetLinkGenerator meetLinkGenerator;

  private Teacher teacher;
  private Student tutor;
  private Subject maths;
  private Subject physics;
  private TutorRole mathsRole;
  private TutorRole physicsRole;
  private StudyRoom room;
  private MockHttpSession session;

  @BeforeEach
  void setUp() throws Exception {
    teacher = teacherRepository.save(new Teacher("it.ed.teacher@example.com", "it.ed.teacher@studymatch.com",
        "Test Teacher", passwordEncoder.encode(PASSWORD)));
    Category category = categoryRepository.save(new Category("IT Edit Category"));
    maths = subjectRepository.save(new Subject("IT-EDIT-MATH", "Test Maths", 6, category));
    physics = subjectRepository.save(new Subject("IT-EDIT-PHYS", "Test Physics", 6, category));
    tutor = saveStudent("it.ed.tutor@example.com", "Tutor Test");
    mathsRole = saveTutorRole(tutor, maths);
    physicsRole = saveTutorRole(tutor, physics);

    room = studyRoomRepository.save(new StudyRoom(mathsRole, tomorrowAt(14), tomorrowAt(16), 5, "test room", LINK));
    session = loginAndGetSession(tutor.getInstitutionalEmail());
  }

  private Student saveStudent(String personalEmail, String name) {
    Degree degree = degreeRepository.save(new Degree("IT Edit Degree " + name, 3));
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

  private static String body(LocalDateTime startsAt, LocalDateTime endsAt, Integer capacity, String link) {
    return """
        {"startsAt": %s, "endsAt": %s, "description": "edited room", "capacity": %s, "link": %s}"""
        .formatted(quote(startsAt), quote(endsAt), capacity, quote(link));
  }

  private static String quote(Object value) {
    return value == null ? "null" : "\"" + value + "\"";
  }

  private ResultActions update(Long roomId, String body) throws Exception {
    return mockMvc.perform(put("/study-rooms/" + roomId).with(csrf()).session(session)
        .contentType(MediaType.APPLICATION_JSON)
        .content(body));
  }

  @Test
  @DisplayName("A tutor edits their room and the new details come back")
  void editsStudyRoom() throws Exception {
    // Act + Assert
    update(room.getId(), body(tomorrowAt(15), tomorrowAt(17), 6, LINK))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(room.getId()))
        .andExpect(jsonPath("$.capacity").value(6))
        .andExpect(jsonPath("$.link").value(LINK));

    StudyRoom saved = studyRoomRepository.findById(room.getId()).orElseThrow();
    assertEquals(tomorrowAt(15), saved.getStartsAt());
    assertEquals(tomorrowAt(17), saved.getEndsAt());
  }

  @Test
  @DisplayName("A student who doesn't host the room can't edit it")
  void notHostCannotEdit() throws Exception {
    // Arrange
    Student other = saveStudent("it.ed.other@example.com", "Other Tutor");
    saveTutorRole(other, maths);
    MockHttpSession otherSession = loginAndGetSession(other.getInstitutionalEmail());

    // Act + Assert
    mockMvc.perform(put("/study-rooms/" + room.getId()).with(csrf()).session(otherSession)
        .contentType(MediaType.APPLICATION_JSON)
        .content(body(tomorrowAt(15), tomorrowAt(17), 6, LINK)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
  }

  @Test
  @DisplayName("Editing to overlap another room of the tutor names the conflicting room")
  void overlapsAnotherRoom() throws Exception {
    // Arrange
    StudyRoom physicsRoom = studyRoomRepository
        .save(new StudyRoom(physicsRole, tomorrowAt(15), tomorrowAt(17), 5, "physics room", LINK));

    // Act + Assert
    update(room.getId(), body(tomorrowAt(14), tomorrowAt(16), 5, LINK))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("STUDYROOM_OVERLAP"))
        .andExpect(jsonPath("$.params.id").value(physicsRoom.getId()));
  }

  @Test
  @DisplayName("Capacity can't go below the number of participants")
  void capacityBelowParticipants() throws Exception {
    // Arrange - three participants
    room.join(saveStudent("it.ed.ana@example.com", "Ana Test"));
    room.join(saveStudent("it.ed.bruno@example.com", "Bruno Test"));
    room.join(saveStudent("it.ed.carla@example.com", "Carla Test"));
    studyRoomRepository.saveAndFlush(room);

    // Act + Assert - capacity 2 is below 3 participants
    update(room.getId(), body(tomorrowAt(14), tomorrowAt(16), 2, LINK))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CAPACITY_BELOW_PARTICIPANTS"))
        .andExpect(jsonPath("$.params.participants").value(3));
  }

  @Test
  @DisplayName("Raising the capacity of a full room makes it scheduled again")
  void fullRoomCapacityIncrease() throws Exception {
    // Arrange - a full room with both places taken, at a time that doesn't clash with the setUp room
    StudyRoom full = studyRoomRepository
        .save(new StudyRoom(mathsRole, tomorrowAt(18), tomorrowAt(20), 2, "full room", LINK));
    full.join(saveStudent("it.ed.ana@example.com", "Ana Test"));
    full.join(saveStudent("it.ed.bruno@example.com", "Bruno Test"));
    studyRoomRepository.saveAndFlush(full);
    assertEquals(StudyRoomStatus.FULL, full.getStatus());

    // Act + Assert
    update(full.getId(), body(tomorrowAt(18), tomorrowAt(20), 3, LINK))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SCHEDULED"))
        .andExpect(jsonPath("$.capacity").value(3));
  }

  @Test
  @DisplayName("A room can't be edited to start in the past")
  void inThePast() throws Exception {
    // Act + Assert
    update(room.getId(), body(tomorrowAt(14).minusDays(2), tomorrowAt(16).minusDays(2), 5, LINK))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("STUDYROOM_IN_PAST"));
  }

  @Test
  @DisplayName("A room can't be edited to end before it starts")
  void endsBeforeStart() throws Exception {
    // Act + Assert
    update(room.getId(), body(tomorrowAt(16), tomorrowAt(14), 5, LINK))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("STUDYROOM_ENDS_BEFORE_START"));
  }

  @Test
  @DisplayName("A room that already started can't be edited")
  void afterStartIsRejected() throws Exception {
    // Arrange - the room started a minute ago
    ReflectionTestUtils.setField(room, "startsAt", LocalDateTime.now().minusMinutes(1));
    studyRoomRepository.saveAndFlush(room);

    // Act + Assert
    update(room.getId(), body(tomorrowAt(15), tomorrowAt(17), 6, LINK))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("STUDYROOM_ALREADY_STARTED"));
  }

  @Test
  @DisplayName("A cancelled room can't be edited")
  void cancelledRoomIsRejected() throws Exception {
    // Arrange
    room.cancel("test reason");
    studyRoomRepository.saveAndFlush(room);

    // Act + Assert
    update(room.getId(), body(tomorrowAt(15), tomorrowAt(17), 6, LINK))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("STUDYROOM_CLOSED"));
  }

  @Test
  @DisplayName("Moving a room to a time that overlaps only its own old slot isn't an overlap")
  void overlappingOwnSlotIsNotAnOverlap() throws Exception {
    // Act - 14-16 moves to 15-17, which overlaps only its own old slot
    update(room.getId(), body(tomorrowAt(15), tomorrowAt(17), 5, LINK))
        .andExpect(status().isOk());

    // Assert - the room really moved
    StudyRoom saved = studyRoomRepository.findById(room.getId()).orElseThrow();
    assertEquals(tomorrowAt(15), saved.getStartsAt());
  }

  @Test
  @DisplayName("Without a link the room is edited with the Meet link the server made")
  void editsWithGeneratedLink() throws Exception {
    // Arrange
    when(meetLinkGenerator.createLink()).thenReturn("https://meet.google.com/xyz-wxyz-xyz");

    // Act + Assert
    update(room.getId(), body(tomorrowAt(15), tomorrowAt(17), 6, null))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.link").value("https://meet.google.com/xyz-wxyz-xyz"));
  }

  @Test
  @DisplayName("Only students (tutors) can edit StudyRooms")
  void teacherCannotEdit() throws Exception {
    // Arrange
    teacher.setMustChangePassword(false);
    teacherRepository.saveAndFlush(teacher);
    MockHttpSession teacherSession = loginAndGetSession(teacher.getInstitutionalEmail());

    // Act + Assert
    mockMvc.perform(put("/study-rooms/" + room.getId()).with(csrf()).session(teacherSession)
        .contentType(MediaType.APPLICATION_JSON)
        .content(body(tomorrowAt(15), tomorrowAt(17), 6, LINK)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
  }

  private List<Notification> notificationsOf(User user) {
    return notificationRepository.findByRecipientNumber(user.getNumber(), Pageable.unpaged()).getContent();
  }

  @Test
  @DisplayName("Editing tells each participant the room's new times")
  void editingNotifiesParticipants() throws Exception {
    // Arrange
    Student ana = saveStudent("it.ed.ana@example.com", "Ana Test");
    room.join(ana);
    room = studyRoomRepository.saveAndFlush(room);

    // Act
    update(room.getId(), body(tomorrowAt(15), tomorrowAt(17), 6, LINK)).andExpect(status().isOk());

    // Assert
    List<Notification> notifications = notificationsOf(ana);
    assertEquals(1, notifications.size());
    Notification notification = notifications.getFirst();
    assertEquals(NotificationType.STUDYROOM_UPDATED, notification.getType());
    assertEquals(NotificationTarget.STUDYROOM, notification.getTarget());
    assertEquals(room.getId(), notification.getTargetId());
    assertEquals(tomorrowAt(15).toString(), notification.getParams().get("startsAt"));
    assertEquals(tomorrowAt(17).toString(), notification.getParams().get("endsAt"));
  }

}
