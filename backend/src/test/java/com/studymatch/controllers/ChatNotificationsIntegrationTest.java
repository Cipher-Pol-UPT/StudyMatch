package com.studymatch.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.springframework.transaction.annotation.Transactional;

import com.studymatch.enums.NotificationTarget;
import com.studymatch.enums.NotificationType;
import com.studymatch.models.Chat;
import com.studymatch.models.CustomChat;
import com.studymatch.models.Degree;
import com.studymatch.models.DirectChat;
import com.studymatch.models.Notification;
import com.studymatch.models.PersonalityProfile;
import com.studymatch.models.Student;
import com.studymatch.models.User;
import com.studymatch.models.UserBlock;
import com.studymatch.repositories.ChatRepository;
import com.studymatch.repositories.DegreeRepository;
import com.studymatch.repositories.NotificationRepository;
import com.studymatch.repositories.StudentRepository;
import com.studymatch.repositories.UserBlockRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ChatNotificationsIntegrationTest {

  private static final String PASSWORD = "Test1234";

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private DegreeRepository degreeRepository;

  @Autowired
  private StudentRepository studentRepository;

  @Autowired
  private ChatRepository chatRepository;

  @Autowired
  private NotificationRepository notificationRepository;

  @Autowired
  private UserBlockRepository userBlockRepository;

  @Autowired
  private PasswordEncoder passwordEncoder;

  private Degree degree;
  private Student ana;
  private Student rui;
  private Student eva;
  private Student zeca;
  private DirectChat withRui;
  private CustomChat group;
  private MockHttpSession anaSession;
  private MockHttpSession ruiSession;

  // ana and rui share a DM, the three of them a group chat, zeca is in neither (rolled back after each test)
  @BeforeEach
  void setUp() throws Exception {
    degree = degreeRepository.save(new Degree("IT Chat Notifications Degree", 3));
    ana = saveStudent("it.chatnotif.ana@example.com", "Ana Notif Test");
    rui = saveStudent("it.chatnotif.rui@example.com", "Rui Notif Test");
    eva = saveStudent("it.chatnotif.eva@example.com", "Eva Notif Test");
    zeca = saveStudent("it.chatnotif.zeca@example.com", "Zeca Notif Test");
    withRui = chatRepository.save(new DirectChat(ana, rui));
    group = chatRepository.save(new CustomChat("Study group", null, ana, List.of(rui, eva)));
    anaSession = loginAs(ana.getInstitutionalEmail());
    ruiSession = loginAs(rui.getInstitutionalEmail());
  }

  private Student saveStudent(String personalEmail, String name) {
    Student student = new Student(personalEmail, name, passwordEncoder.encode(PASSWORD), degree, 1);
    student.setMustChangePassword(false);
    student.setPersonalityProfile(new PersonalityProfile(0.5, 0.5, 0.5, 0.5, 0.5));
    student = studentRepository.save(student);
    student.generateInstitutionalEmail("studymatch.com");
    return studentRepository.save(student);
  }

  private MockHttpSession loginAs(String email) throws Exception {
    MvcResult result = mockMvc.perform(post("/users/login").with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
        .andReturn();
    assertEquals(200, result.getResponse().getStatus());
    return (MockHttpSession) result.getRequest().getSession(false);
  }

  private void send(Chat chat, MockHttpSession session, String text) throws Exception {
    mockMvc.perform(post("/chats/" + chat.getId() + "/messages").with(csrf()).session(session)
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"text\":\"" + text + "\"}"))
        .andExpect(status().isCreated());
  }

  private void setLevel(Chat chat, MockHttpSession session, String level) throws Exception {
    mockMvc.perform(put("/chats/" + chat.getId() + "/notification-level").with(csrf()).session(session)
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"level\":\"" + level + "\"}"))
        .andExpect(status().isNoContent());
  }

  // the user's chat notifications for that chat, read ones included
  private List<Notification> notificationsOf(User user, Chat chat) {
    return notificationRepository.findAll().stream()
        .filter(notification -> notification.getRecipient().getNumber().equals(user.getNumber()))
        .filter(notification -> notification.getType() == NotificationType.CHAT_MESSAGES)
        .filter(notification -> notification.getTargetId().equals(chat.getId()))
        .toList();
  }

  // a message arriving

  @Test
  @DisplayName("A message in a DM gives the other person an unread \"1 new message\" named after the sender")
  void firstMessage() throws Exception {
    // Act
    send(withRui, ruiSession, "hi Ana");

    // Assert
    List<Notification> notifications = notificationsOf(ana, withRui);
    assertEquals(1, notifications.size());
    Notification notification = notifications.getFirst();
    assertEquals(NotificationTarget.CHAT, notification.getTarget());
    assertEquals("Rui Notif Test", notification.getParams().get("chat"));
    assertEquals(1, notification.getParams().get("count"));
    assertEquals(0, notification.getParams().get("mentions"));
    assertEquals(false, notification.isRead());
  }

  @Test
  @DisplayName("Your own messages never notify you (GR11)")
  void ownMessage() throws Exception {
    // Act
    send(withRui, ruiSession, "hi Ana");

    // Assert
    assertTrue(notificationsOf(rui, withRui).isEmpty());
  }

  @Test
  @DisplayName("Three messages are one unread notification of 3, the list shows it as one")
  void countedIntoOne() throws Exception {
    // Act
    send(withRui, ruiSession, "hi");
    send(withRui, ruiSession, "are you there");
    send(withRui, ruiSession, "Ana?");

    // Assert
    List<Notification> notifications = notificationsOf(ana, withRui);
    assertEquals(1, notifications.size());
    assertEquals(3, notifications.getFirst().getParams().get("count"));
    mockMvc.perform(get("/notifications/").session(anaSession))
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.content[0].type").value("CHAT_MESSAGES"))
        .andExpect(jsonPath("$.content[0].target").value("CHAT"))
        .andExpect(jsonPath("$.content[0].targetId").value(withRui.getId()))
        .andExpect(jsonPath("$.content[0].params.count").value(3));
  }

  @Test
  @DisplayName("Opening the chat reads its notification, and the next message starts a new one of 1")
  void openingReads() throws Exception {
    // Arrange
    send(withRui, ruiSession, "hi");
    send(withRui, ruiSession, "hello?");

    // Act
    mockMvc.perform(put("/chats/" + withRui.getId() + "/read").with(csrf()).session(anaSession))
        .andExpect(status().isNoContent());
    send(withRui, ruiSession, "there you are");

    // Assert
    List<Notification> notifications = notificationsOf(ana, withRui);
    assertEquals(2, notifications.size());
    assertEquals(1, notifications.stream().filter(notification -> !notification.isRead()).count());
    Notification unread = notifications.stream().filter(notification -> !notification.isRead()).findFirst()
        .orElseThrow();
    assertEquals(1, unread.getParams().get("count"));
  }

  @Test
  @DisplayName("Opening one chat leaves another chat's notification unread")
  void openingOnlyThatChat() throws Exception {
    // Arrange
    send(withRui, ruiSession, "hi");
    send(group, ruiSession, "hi all");

    // Act
    mockMvc.perform(put("/chats/" + withRui.getId() + "/read").with(csrf()).session(anaSession))
        .andExpect(status().isNoContent());

    // Assert
    assertEquals(false, notificationsOf(ana, group).getFirst().isRead());
  }

  @Test
  @DisplayName("A group chat message notifies every other member under the group's name")
  void groupMessage() throws Exception {
    // Act
    send(group, ruiSession, "meeting at 3");

    // Assert
    assertEquals("Study group", notificationsOf(ana, group).getFirst().getParams().get("chat"));
    assertEquals("Study group", notificationsOf(eva, group).getFirst().getParams().get("chat"));
    assertTrue(notificationsOf(rui, group).isEmpty());
  }

  // levels

  @Test
  @DisplayName("On None a chat never notifies, the other members still are")
  void levelNone() throws Exception {
    // Arrange
    setLevel(group, anaSession, "NONE");

    // Act
    send(group, ruiSession, "meeting at 3");

    // Assert
    assertTrue(notificationsOf(ana, group).isEmpty());
    assertEquals(1, notificationsOf(eva, group).size());
  }

  @Test
  @DisplayName("On Only @mentions a message that mentions nobody doesn't notify")
  void levelMentions() throws Exception {
    // Arrange
    setLevel(group, anaSession, "MENTIONS");

    // Act
    send(group, ruiSession, "meeting at 3");

    // Assert
    assertTrue(notificationsOf(ana, group).isEmpty());
  }

  @Test
  @DisplayName("Back on All after None, messages notify again")
  void levelBackToAll() throws Exception {
    // Arrange
    setLevel(group, anaSession, "NONE");
    setLevel(group, anaSession, "ALL");

    // Act
    send(group, ruiSession, "meeting at 3");

    // Assert
    assertEquals(1, notificationsOf(ana, group).size());
  }

  @Test
  @DisplayName("The level is All for a chat never changed, and what was picked afterwards")
  void getLevel() throws Exception {
    // Act + Assert
    mockMvc.perform(get("/chats/" + group.getId() + "/notification-level").session(anaSession))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.level").value("ALL"));
    setLevel(group, anaSession, "MENTIONS");
    mockMvc.perform(get("/chats/" + group.getId() + "/notification-level").session(anaSession))
        .andExpect(jsonPath("$.level").value("MENTIONS"));
  }

  @Test
  @DisplayName("Picking a level keeps what was read, the chat doesn't go back to unread")
  void levelKeepsRead() throws Exception {
    // Arrange
    send(group, ruiSession, "meeting at 3");
    mockMvc.perform(put("/chats/" + group.getId() + "/read").with(csrf()).session(anaSession))
        .andExpect(status().isNoContent());

    // Act
    setLevel(group, anaSession, "MENTIONS");

    // Assert
    mockMvc.perform(get("/chats/unread-count").session(anaSession))
        .andExpect(jsonPath("$").value(0));
  }

  @Test
  @DisplayName("Someone who isn't in the chat gets 404 NOT_FOUND for its level, reading or changing it (GR8)")
  void levelNotMember() throws Exception {
    // Arrange
    MockHttpSession zecaSession = loginAs(zeca.getInstitutionalEmail());

    // Act + Assert
    mockMvc.perform(get("/chats/" + group.getId() + "/notification-level").session(zecaSession))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    mockMvc.perform(put("/chats/" + group.getId() + "/notification-level").with(csrf()).session(zecaSession)
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"level\":\"NONE\"}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
  }

  @Test
  @DisplayName("No level is 400 VALIDATION_FAILED")
  void levelMissing() throws Exception {
    // Act + Assert
    mockMvc.perform(put("/chats/" + group.getId() + "/notification-level").with(csrf()).session(anaSession)
        .contentType(MediaType.APPLICATION_JSON)
        .content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
  }

  // blocks

  @Test
  @DisplayName("A blocked user's DM message is hidden from the blocker, so it doesn't notify them (GR9)")
  void blockedInDirectChat() throws Exception {
    // Arrange
    userBlockRepository.save(new UserBlock(ana, rui));

    // Act
    send(withRui, ruiSession, "hi Ana");

    // Assert
    assertTrue(notificationsOf(ana, withRui).isEmpty());
  }

  @Test
  @DisplayName("In a group chat someone who blocked the sender isn't notified, the others are (GR9)")
  void blockedInGroupChat() throws Exception {
    // Arrange
    userBlockRepository.save(new UserBlock(eva, rui));

    // Act
    send(group, ruiSession, "meeting at 3");

    // Assert
    assertTrue(notificationsOf(eva, group).isEmpty());
    assertEquals(1, notificationsOf(ana, group).size());
  }

}
