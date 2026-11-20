package com.studymatch.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
import com.studymatch.models.Participation;
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
class ConfirmAttendanceIntegrationTest {

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

  private Student ana;
  private Student bruno;
  private StudyRoom room;
  private MockHttpSession session;

  // a tutor logged in, with a room ana and bruno joined, which ended an hour ago (rolled back after each test)
  @BeforeEach
  void setUp() throws Exception {
    Teacher teacher = teacherRepository.save(new Teacher("it.ca.teacher@example.com", "it.ca.teacher@studymatch.com",
        "Test Teacher", passwordEncoder.encode(PASSWORD)));
    Category category = categoryRepository.save(new Category("IT Attendance Category"));
    Subject maths = subjectRepository.save(new Subject("IT-CA-MATH", "Test Maths", 6, category));
    Student tutor = saveStudent("it.ca.tutor@example.com", "Tutor Test");
    TutorRole role = tutorRoleRepository.save(tutorApplicationRepository
        .save(new TutorApplication(tutor, maths, "test motivation")).approve(teacher));
    ana = saveStudent("it.ca.ana@example.com", "Ana Test");
    bruno = saveStudent("it.ca.bruno@example.com", "Bruno Test");

    room = new StudyRoom(role, tomorrowAt(14), tomorrowAt(16), 5, "test room", LINK);
    room.join(bruno);
    room.join(ana);
    endRoomAt(LocalDateTime.now().minusHours(1));
    session = loginAndGetSession(tutor.getInstitutionalEmail());
  }

  private Student saveStudent(String personalEmail, String name) {
    Degree degree = degreeRepository.save(new Degree("IT Attendance Degree " + name, 3));
    Student student = new Student(personalEmail, name, passwordEncoder.encode(PASSWORD), degree, 1);
    student.setMustChangePassword(false);
    student.setPersonalityProfile(new PersonalityProfile(0.5, 0.5, 0.5, 0.5));
    student = studentRepository.save(student);
    student.generateInstitutionalEmail("studymatch.com");
    return studentRepository.saveAndFlush(student);
  }

  private MockHttpSession loginAndGetSession(String email) throws Exception {
    MvcResult result = mockMvc.perform(post("/users/login").with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
        .andReturn();
    assertEquals(200, result.getResponse().getStatus());
    return (MockHttpSession) result.getRequest().getSession(false);
  }

  // the constructor won't take past dates, so the room is moved after the students joined
  private void endRoomAt(LocalDateTime endsAt) {
    ReflectionTestUtils.setField(room, "startsAt", endsAt.minusHours(2));
    ReflectionTestUtils.setField(room, "endsAt", endsAt);
    room = studyRoomRepository.saveAndFlush(room);
  }

  private static LocalDateTime tomorrowAt(int hour) {
    return LocalDateTime.now().plusDays(1).truncatedTo(ChronoUnit.DAYS).withHour(hour);
  }

  private ResultActions confirm(Long roomId, String body) throws Exception {
    return mockMvc.perform(post("/study-rooms/" + roomId + "/attendance").with(csrf()).session(session)
        .contentType(MediaType.APPLICATION_JSON)
        .content(body));
  }

  private boolean attended(Student student) {
    return studyRoomRepository.findById(room.getId()).orElseThrow().getParticipations().stream()
        .filter(p -> p.getStudent().getNumber().equals(student.getNumber()))
        .findFirst()
        .map(Participation::isAttended)
        .orElseThrow();
  }

  @Test
  @DisplayName("The host sees who joined, sorted by name, none attended yet")
  void hostListsParticipants() throws Exception {
    // Act + Assert
    mockMvc.perform(get("/study-rooms/" + room.getId() + "/participants").session(session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[0].number").value(ana.getNumber()))
        .andExpect(jsonPath("$[0].name").value("Ana Test"))
        .andExpect(jsonPath("$[0].attended").value(false))
        .andExpect(jsonPath("$[1].name").value("Bruno Test"));
  }

  @Test
  @DisplayName("The host confirms ana, the room is COMPLETED and bruno counts as absent")
  void hostConfirmsAttendance() throws Exception {
    // Act
    confirm(room.getId(), "{\"attendeeNumbers\": [" + ana.getNumber() + "]}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("COMPLETED"));

    // Assert
    assertTrue(attended(ana));
    assertFalse(attended(bruno));
  }

  @Test
  @DisplayName("Confirming with nobody completes the room with no attendees")
  void confirmNobody() throws Exception {
    // Act
    confirm(room.getId(), "{\"attendeeNumbers\": []}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("COMPLETED"));

    // Assert
    assertFalse(attended(ana));
    assertFalse(attended(bruno));
  }

  @Test
  @DisplayName("Confirming a second time gets 409 STUDYROOM_CLOSED and the first answer stays")
  void confirmTwice() throws Exception {
    // Arrange
    confirm(room.getId(), "{\"attendeeNumbers\": [" + ana.getNumber() + "]}").andExpect(status().isOk());

    // Act
    confirm(room.getId(), "{\"attendeeNumbers\": [" + bruno.getNumber() + "]}")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("STUDYROOM_CLOSED"));

    // Assert
    assertTrue(attended(ana));
    assertFalse(attended(bruno));
  }

  @Test
  @DisplayName("A student who didn't join gets 400 ATTENDEE_NOT_A_PARTICIPANT with their number")
  void attendeeWhoDidNotJoin() throws Exception {
    // Act + Assert
    confirm(room.getId(), "{\"attendeeNumbers\": [" + ana.getNumber() + ", 999999]}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("ATTENDEE_NOT_A_PARTICIPANT"))
        .andExpect(jsonPath("$.params.number").value(999999));
    assertFalse(attended(ana));
  }

  @Test
  @DisplayName("A room that hasn't ended yet gets 409 STUDYROOM_NOT_ENDED")
  void roomNotEnded() throws Exception {
    // Arrange - ends in an hour
    endRoomAt(LocalDateTime.now().plusHours(1));

    // Act + Assert
    confirm(room.getId(), "{\"attendeeNumbers\": []}")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("STUDYROOM_NOT_ENDED"));
  }

  @Test
  @DisplayName("After 48 hours the room is already COMPLETED and it's too late to confirm")
  void afterTheWindow() throws Exception {
    // Arrange
    endRoomAt(LocalDateTime.now().minusHours(StudyRoom.CONFIRMATION_WINDOW_HOURS + 1L));

    // Act + Assert
    confirm(room.getId(), "{\"attendeeNumbers\": [" + ana.getNumber() + "]}")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("STUDYROOM_CLOSED"));
    assertFalse(attended(ana));
  }

  @Test
  @DisplayName("A participant who isn't the host gets ACCESS_DENIED")
  void notHost() throws Exception {
    // Arrange
    session = loginAndGetSession(ana.getInstitutionalEmail());

    // Act + Assert
    confirm(room.getId(), "{\"attendeeNumbers\": [" + ana.getNumber() + "]}")
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    mockMvc.perform(get("/study-rooms/" + room.getId() + "/participants").session(session))
        .andExpect(status().isForbidden());
    assertFalse(attended(ana));
  }

  @Test
  @DisplayName("A room that doesn't exist gets 404 NOT_FOUND")
  void missingRoom() throws Exception {
    // Act + Assert
    confirm(room.getId() + 1000, "{\"attendeeNumbers\": []}")
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
  }

  @Test
  @DisplayName("The list of attendees is required, even if empty")
  void missingList() throws Exception {
    // Act + Assert
    confirm(room.getId(), "{}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.fieldErrors[0].field").value("attendeeNumbers"));
  }

}
