package com.studymatch.controllers;

import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

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
import org.springframework.transaction.annotation.Transactional;

import com.studymatch.enums.NotificationTarget;
import com.studymatch.enums.NotificationType;
import com.studymatch.models.Admin;
import com.studymatch.models.Degree;
import com.studymatch.models.Notification;
import com.studymatch.models.PersonalityProfile;
import com.studymatch.models.Staff;
import com.studymatch.models.Student;
import com.studymatch.models.Teacher;
import com.studymatch.models.User;
import com.studymatch.repositories.DegreeRepository;
import com.studymatch.repositories.NotificationRepository;
import com.studymatch.repositories.StudentRepository;
import com.studymatch.repositories.UserRepository;

import jakarta.persistence.EntityManager;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ViewNotificationsIntegrationTest {

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
  private UserRepository userRepository;
  @Autowired
  private NotificationRepository notificationRepository;
  @Autowired
  private EntityManager entityManager;

  private Student ana;
  private Student bruno;
  private Notification older;
  private Notification newer;
  private Notification brunos;
  private MockHttpSession session;

  // ana has two notifications and bruno one (everything is rolled back after each test)
  @BeforeEach
  void setUp() throws Exception {
    ana = saveStudent("it.notifications.ana@example.com", "Ana Test", true);
    bruno = saveStudent("it.notifications.bruno@example.com", "Bruno Test", true);

    older = notificationRepository.save(new Notification(ana, NotificationType.STUDYROOM_CANCELLED,
        NotificationTarget.STUDYROOM, 20L, Map.of("subject", "Test Maths", "reason", "tutor is sick")));
    newer = notificationRepository.save(new Notification(ana, NotificationType.ACCOUNT_UNBLOCKED, null, null,
        Map.of()));
    // a minute apart, so the order doesn't depend on the clock
    ReflectionTestUtils.setField(older, "createdAt", newer.getCreatedAt().minusMinutes(1));
    notificationRepository.saveAndFlush(older);
    brunos = notificationRepository.saveAndFlush(new Notification(bruno, NotificationType.STUDYROOM_LEFT,
        NotificationTarget.STUDYROOM, 20L, Map.of("student", "Ana Test", "number", ana.getNumber())));
    // so the requests read them back from the database, params and all
    entityManager.clear();

    session = loginAndGetSession(ana.getInstitutionalEmail());
  }

  private Student saveStudent(String personalEmail, String name, boolean tookPersonalityTest) {
    Degree degree = degreeRepository.save(new Degree("IT Notifications Degree " + name, 3));
    Student student = new Student(personalEmail, name, passwordEncoder.encode(PASSWORD), degree, 1);
    student.setMustChangePassword(false);
    if (tookPersonalityTest) {
      student.setPersonalityProfile(new PersonalityProfile(0.5, 0.5, 0.5, 0.5, 0.5));
    }
    student = studentRepository.save(student);
    student.generateInstitutionalEmail("studymatch.com");
    return studentRepository.saveAndFlush(student);
  }

  private <T extends User> T saveEmployee(T user) {
    user.setMustChangePassword(false);
    return userRepository.saveAndFlush(user);
  }

  private MockHttpSession loginAndGetSession(String email) throws Exception {
    MvcResult result = mockMvc.perform(post("/users/login").with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
        .andReturn();
    assertEquals(200, result.getResponse().getStatus());
    return (MockHttpSession) result.getRequest().getSession(false);
  }

  private boolean isRead(Notification notification) {
    return notificationRepository.findById(notification.getId()).orElseThrow().isRead();
  }

  @Test
  @DisplayName("The user sees only their own notifications, newest first, with code, params, target and read state")
  void listsOwnNotificationsNewestFirst() throws Exception {
    // Act + Assert
    mockMvc.perform(get("/notifications/").session(session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(2))
        .andExpect(jsonPath("$.page").value(0))
        .andExpect(jsonPath("$.size").value(20))
        .andExpect(jsonPath("$.content[0].id").value(newer.getId()))
        .andExpect(jsonPath("$.content[0].type").value("ACCOUNT_UNBLOCKED"))
        .andExpect(jsonPath("$.content[0].target").value(nullValue()))
        .andExpect(jsonPath("$.content[0].targetId").value(nullValue()))
        .andExpect(jsonPath("$.content[1].id").value(older.getId()))
        .andExpect(jsonPath("$.content[1].type").value("STUDYROOM_CANCELLED"))
        .andExpect(jsonPath("$.content[1].params.subject").value("Test Maths"))
        .andExpect(jsonPath("$.content[1].params.reason").value("tutor is sick"))
        .andExpect(jsonPath("$.content[1].target").value("STUDYROOM"))
        .andExpect(jsonPath("$.content[1].targetId").value(20))
        .andExpect(jsonPath("$.content[1].read").value(false))
        .andExpect(jsonPath("$.content[1].createdAt").exists());
  }

  @Test
  @DisplayName("A user without notifications gets an empty page")
  void noNotifications() throws Exception {
    // Arrange
    Student carla = saveStudent("it.notifications.carla@example.com", "Carla Test", true);

    // Act + Assert
    mockMvc.perform(get("/notifications/").session(loginAndGetSession(carla.getInstitutionalEmail())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(0))
        .andExpect(jsonPath("$.content").isEmpty());
  }

  @Test
  @DisplayName("The unread count is only the user's own unread notifications")
  void countsOwnUnread() throws Exception {
    // Arrange
    older.markAsRead();
    notificationRepository.saveAndFlush(older);

    // Act + Assert
    mockMvc.perform(get("/notifications/unread-count").session(session))
        .andExpect(status().isOk())
        .andExpect(content().string("1"));
  }

  @Test
  @DisplayName("Selecting a notification marks it as read, and only that one")
  void marksOneAsRead() throws Exception {
    // Act
    mockMvc.perform(put("/notifications/" + older.getId() + "/read").with(csrf()).session(session))
        .andExpect(status().isNoContent());

    // Assert
    assertTrue(isRead(older));
    assertFalse(isRead(newer));
  }

  @Test
  @DisplayName("Marking a notification that's already read changes nothing")
  void markingTwiceChangesNothing() throws Exception {
    // Arrange
    mockMvc.perform(put("/notifications/" + older.getId() + "/read").with(csrf()).session(session));

    // Act + Assert
    mockMvc.perform(put("/notifications/" + older.getId() + "/read").with(csrf()).session(session))
        .andExpect(status().isNoContent());
    assertTrue(isRead(older));
  }

  @Test
  @DisplayName("Marking someone else's notification is NOT_FOUND and it stays unread")
  void cannotMarkOthersNotification() throws Exception {
    // Act + Assert
    mockMvc.perform(put("/notifications/" + brunos.getId() + "/read").with(csrf()).session(session))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    assertFalse(isRead(brunos));
  }

  @Test
  @DisplayName("A notification that doesn't exist is NOT_FOUND")
  void unknownNotification() throws Exception {
    mockMvc.perform(put("/notifications/9999999/read").with(csrf()).session(session))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
  }

  @Test
  @DisplayName("Mark all as read marks every one of the user's notifications, nobody else's")
  void marksAllOwnAsRead() throws Exception {
    // Act
    mockMvc.perform(put("/notifications/read").with(csrf()).session(session))
        .andExpect(status().isNoContent());

    // Assert
    assertTrue(isRead(older));
    assertTrue(isRead(newer));
    assertFalse(isRead(brunos));
    mockMvc.perform(get("/notifications/unread-count").session(session))
        .andExpect(content().string("0"));
  }

  @Test
  @DisplayName("Teachers, Staff and Admins see their notifications too")
  void everyRoleCanView() throws Exception {
    // Arrange
    Teacher teacher = saveEmployee(new Teacher("it.notifications.teacher@example.com",
        "it.notifications.teacher@studymatch.com", "Test Teacher", passwordEncoder.encode(PASSWORD)));
    Staff staff = saveEmployee(new Staff("it.notifications.staff@example.com",
        "it.notifications.staff@studymatch.com", "Test Staff", passwordEncoder.encode(PASSWORD)));
    Admin admin = saveEmployee(new Admin("it.notifications.admin@example.com",
        "it.notifications.admin@studymatch.com", "Test Admin", passwordEncoder.encode(PASSWORD)));
    notificationRepository.saveAndFlush(new Notification(teacher, NotificationType.TUTOR_APPLICATION_SUBMITTED,
        NotificationTarget.TUTOR_APPLICATION, 5L, Map.of("student", "Ana Test")));

    // Act + Assert
    for (User user : new User[] { teacher, staff, admin }) {
      MockHttpSession userSession = loginAndGetSession(user.getInstitutionalEmail());
      mockMvc.perform(get("/notifications/").session(userSession)).andExpect(status().isOk());
      mockMvc.perform(get("/notifications/unread-count").session(userSession)).andExpect(status().isOk());
      mockMvc.perform(put("/notifications/read").with(csrf()).session(userSession))
          .andExpect(status().isNoContent());
    }
  }

  @Test
  @DisplayName("A Student who hasn't taken the personality test can't see notifications yet (GR1)")
  void personalityTestGate() throws Exception {
    // Arrange
    Student newStudent = saveStudent("it.notifications.new@example.com", "New Student", false);
    MockHttpSession newSession = loginAndGetSession(newStudent.getInstitutionalEmail());

    // Act + Assert
    mockMvc.perform(get("/notifications/unread-count").session(newSession))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("PERSONALITY_TEST_REQUIRED"));
    mockMvc.perform(get("/notifications/").session(newSession))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("PERSONALITY_TEST_REQUIRED"));
  }

  @Test
  @DisplayName("Nobody logged in gets 401, and marking without the CSRF token is refused")
  void unauthenticatedAndCsrf() throws Exception {
    mockMvc.perform(get("/notifications/")).andExpect(status().isUnauthorized());
    mockMvc.perform(get("/notifications/unread-count")).andExpect(status().isUnauthorized());
    mockMvc.perform(put("/notifications/read").with(csrf())).andExpect(status().isUnauthorized());
    mockMvc.perform(put("/notifications/read").session(session))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("CSRF_TOKEN_INVALID"));
    assertFalse(isRead(older));
  }

}
