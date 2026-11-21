package com.studymatch.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.ArrayList;
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

import com.studymatch.models.ChatSettings;
import com.studymatch.models.Degree;
import com.studymatch.models.DirectChat;
import com.studymatch.models.Message;
import com.studymatch.models.PersonalityProfile;
import com.studymatch.models.Staff;
import com.studymatch.models.Student;
import com.studymatch.models.Teacher;
import com.studymatch.models.User;
import com.studymatch.repositories.ChatRepository;
import com.studymatch.repositories.ChatSettingsRepository;
import com.studymatch.repositories.DegreeRepository;
import com.studymatch.repositories.MessageRepository;
import com.studymatch.repositories.StaffRepository;
import com.studymatch.repositories.StudentRepository;
import com.studymatch.repositories.TeacherRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class OpenChatIntegrationTest {

  private static final String PASSWORD = "Test1234";

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private DegreeRepository degreeRepository;

  @Autowired
  private StudentRepository studentRepository;

  @Autowired
  private TeacherRepository teacherRepository;

  @Autowired
  private StaffRepository staffRepository;

  @Autowired
  private ChatRepository chatRepository;

  @Autowired
  private MessageRepository messageRepository;

  @Autowired
  private ChatSettingsRepository chatSettingsRepository;

  @Autowired
  private PasswordEncoder passwordEncoder;

  private Degree degree;
  private Student ana;
  private Student rui;
  private Student eva;
  private DirectChat withRui;
  private MockHttpSession anaSession;

  // ana and rui share an empty DM, eva isn't in it (rolled back after each test)
  @BeforeEach
  void setUp() throws Exception {
    degree = degreeRepository.save(new Degree("IT Open Chat Degree", 3));
    ana = saveStudent("it.open.ana@example.com", "Ana Open Test");
    rui = saveStudent("it.open.rui@example.com", "Rui Open Test");
    eva = saveStudent("it.open.eva@example.com", "Eva Open Test");
    withRui = chatRepository.save(new DirectChat(ana, rui));
    anaSession = loginAs(ana.getInstitutionalEmail());
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

  private Message message(DirectChat chat, User author, String text) {
    return messageRepository.save(new Message(chat, author, text));
  }

  // "1", "2"... alternating authors, oldest first
  private List<Message> messages(int count) {
    List<Message> sent = new ArrayList<>();
    for (int i = 1; i <= count; i++) {
      sent.add(message(withRui, i % 2 == 0 ? ana : rui, String.valueOf(i)));
    }
    return sent;
  }

  // the sleep keeps the next messages strictly after lastReadAt
  private void markRead(MockHttpSession session) throws Exception {
    mockMvc.perform(put("/chats/" + withRui.getId() + "/read").with(csrf()).session(session))
        .andExpect(status().isNoContent());
    Thread.sleep(5);
  }

  private long settingsRows() {
    return chatSettingsRepository.findAll().stream()
        .filter(settings -> settings.getChat().getId().equals(withRui.getId()))
        .count();
  }

  // the messages

  @Test
  @DisplayName("Opening a chat gives its messages oldest first, with the author and time")
  void messagesOldestFirst() throws Exception {
    // Arrange
    message(withRui, ana, "hi Rui");
    Message reply = message(withRui, rui, "hey Ana");

    // Act + Assert
    mockMvc.perform(get("/chats/" + withRui.getId() + "/messages").session(anaSession))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.messages.length()").value(2))
        .andExpect(jsonPath("$.messages[0].text").value("hi Rui"))
        .andExpect(jsonPath("$.messages[1].id").value(reply.getId()))
        .andExpect(jsonPath("$.messages[1].author.number").value(rui.getNumber()))
        .andExpect(jsonPath("$.messages[1].author.name").value("Rui Open Test"))
        .andExpect(jsonPath("$.messages[1].sentAt").exists())
        .andExpect(jsonPath("$.messages[1].editedAt").doesNotExist())
        .andExpect(jsonPath("$.hasMore").value(false));
  }

  @Test
  @DisplayName("An edited message comes with when it was edited, so it shows \"(edited)\"")
  void editedMessage() throws Exception {
    // Arrange
    Message message = message(withRui, rui, "typo");
    message.edit("fixed");
    messageRepository.save(message);

    // Act + Assert
    mockMvc.perform(get("/chats/" + withRui.getId() + "/messages").session(anaSession))
        .andExpect(jsonPath("$.messages[0].text").value("fixed"))
        .andExpect(jsonPath("$.messages[0].editedAt").exists());
  }

  @Test
  @DisplayName("Exactly 50 messages: all of them, and nothing older to load")
  void exactlyOnePage() throws Exception {
    // Arrange
    messages(50);

    // Act + Assert
    mockMvc.perform(get("/chats/" + withRui.getId() + "/messages").session(anaSession))
        .andExpect(jsonPath("$.messages.length()").value(50))
        .andExpect(jsonPath("$.messages[0].text").value("1"))
        .andExpect(jsonPath("$.messages[49].text").value("50"))
        .andExpect(jsonPath("$.hasMore").value(false));
  }

  @Test
  @DisplayName("51 messages: the newest 50, and scrolling up from the oldest shown gives the first one")
  void scrollingUp() throws Exception {
    // Arrange
    List<Message> sent = messages(51);

    // Act + Assert - the first page leaves out message 1
    mockMvc.perform(get("/chats/" + withRui.getId() + "/messages").session(anaSession))
        .andExpect(jsonPath("$.messages.length()").value(50))
        .andExpect(jsonPath("$.messages[0].text").value("2"))
        .andExpect(jsonPath("$.messages[49].text").value("51"))
        .andExpect(jsonPath("$.hasMore").value(true));
    mockMvc.perform(get("/chats/" + withRui.getId() + "/messages")
        .param("before", sent.get(1).getId().toString()).session(anaSession))
        .andExpect(jsonPath("$.messages.length()").value(1))
        .andExpect(jsonPath("$.messages[0].text").value("1"))
        .andExpect(jsonPath("$.hasMore").value(false));
  }

  @Test
  @DisplayName("A message sent after the first page doesn't shift the next one")
  void newMessageDoesntShiftPages() throws Exception {
    // Arrange - the page shows 3 and 4, then 5 arrives
    List<Message> sent = messages(4);
    message(withRui, rui, "5");

    // Act + Assert
    mockMvc.perform(get("/chats/" + withRui.getId() + "/messages")
        .param("before", sent.get(2).getId().toString()).session(anaSession))
        .andExpect(jsonPath("$.messages.length()").value(2))
        .andExpect(jsonPath("$.messages[0].text").value("1"))
        .andExpect(jsonPath("$.messages[1].text").value("2"));
  }

  @Test
  @DisplayName("Other chats' messages aren't in the page")
  void onlyThisChat() throws Exception {
    // Arrange
    DirectChat withEva = chatRepository.save(new DirectChat(ana, eva));
    message(withEva, eva, "other chat");
    message(withRui, rui, "this chat");

    // Act + Assert
    mockMvc.perform(get("/chats/" + withRui.getId() + "/messages").session(anaSession))
        .andExpect(jsonPath("$.messages.length()").value(1))
        .andExpect(jsonPath("$.messages[0].text").value("this chat"));
  }

  @Test
  @DisplayName("Teachers open their chats too")
  void teacherOpens() throws Exception {
    // Arrange
    Teacher teo = new Teacher("it.open.teo@example.com", "it.open.teo@studymatch.com", "Teo Open Test",
        passwordEncoder.encode(PASSWORD));
    teo.setMustChangePassword(false);
    teo = teacherRepository.save(teo);
    DirectChat withTeo = chatRepository.save(new DirectChat(teo, ana));
    message(withTeo, ana, "hello teacher");

    // Act + Assert
    mockMvc.perform(get("/chats/" + withTeo.getId() + "/messages").session(loginAs(teo.getInstitutionalEmail())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.messages[0].text").value("hello teacher"));
  }

  // marking as read

  @Test
  @DisplayName("Loading the messages alone doesn't mark the chat as read")
  void loadingDoesntMarkRead() throws Exception {
    // Arrange
    message(withRui, rui, "unread");

    // Act
    mockMvc.perform(get("/chats/" + withRui.getId() + "/messages").session(anaSession))
        .andExpect(status().isOk());

    // Assert
    mockMvc.perform(get("/chats/unread-count").session(anaSession))
        .andExpect(content().string("1"));
  }

  @Test
  @DisplayName("Marking as read clears the chat's unread and the side menu's total, new messages count again")
  void markReadClearsUnread() throws Exception {
    // Arrange
    message(withRui, rui, "one");
    message(withRui, rui, "two");

    // Act
    markRead(anaSession);

    // Assert
    mockMvc.perform(get("/chats/").session(anaSession))
        .andExpect(jsonPath("$[0].unread").value(0));
    mockMvc.perform(get("/chats/unread-count").session(anaSession))
        .andExpect(content().string("0"));
    message(withRui, rui, "three");
    mockMvc.perform(get("/chats/unread-count").session(anaSession))
        .andExpect(content().string("1"));
  }

  @Test
  @DisplayName("Marking as read only counts for you, the other person's unread stay")
  void markReadIsPerUser() throws Exception {
    // Arrange
    message(withRui, ana, "for Rui");

    // Act
    markRead(anaSession);

    // Assert
    mockMvc.perform(get("/chats/unread-count").session(loginAs(rui.getInstitutionalEmail())))
        .andExpect(content().string("1"));
  }

  @Test
  @DisplayName("Marking as read again moves lastReadAt forward in the same row")
  void markReadTwice() throws Exception {
    // Arrange
    markRead(anaSession);
    message(withRui, rui, "after the first time");

    // Act
    markRead(anaSession);

    // Assert
    assertEquals(1, settingsRows());
    mockMvc.perform(get("/chats/unread-count").session(anaSession))
        .andExpect(content().string("0"));
  }

  @Test
  @DisplayName("A read that finishes late with an older time doesn't move lastReadAt back")
  void olderReadDoesntMoveBack() throws Exception {
    // Arrange - the older read started before rui's message, the newer one after it
    LocalDateTime olderRead = LocalDateTime.now().minusMinutes(1);
    message(withRui, rui, "seen");
    markRead(anaSession);

    // Act
    chatSettingsRepository.upsertLastReadAt(withRui.getId(), ana.getNumber(), olderRead,
        new ChatSettings(withRui, ana).getNotificationLevel().name());

    // Assert
    assertEquals(1, settingsRows());
    mockMvc.perform(get("/chats/unread-count").session(anaSession))
        .andExpect(content().string("0"));
  }

  // who can

  @Test
  @DisplayName("Someone who isn't in the chat gets NOT_FOUND, as if it didn't exist (GR8)")
  void nonMemberNotFound() throws Exception {
    // Arrange
    message(withRui, rui, "private");
    MockHttpSession evaSession = loginAs(eva.getInstitutionalEmail());

    // Act + Assert
    mockMvc.perform(get("/chats/" + withRui.getId() + "/messages").session(evaSession))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    mockMvc.perform(put("/chats/" + withRui.getId() + "/read").with(csrf()).session(evaSession))
        .andExpect(status().isNotFound());
    assertEquals(0, settingsRows());
  }

  @Test
  @DisplayName("A chat that doesn't exist: NOT_FOUND")
  void missingChat() throws Exception {
    // Act + Assert
    mockMvc.perform(get("/chats/0/messages").session(anaSession))
        .andExpect(status().isNotFound());
    mockMvc.perform(put("/chats/0/read").with(csrf()).session(anaSession))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("Staff don't have chats: 403")
  void staffForbidden() throws Exception {
    // Arrange
    Staff staff = new Staff("it.open.staff@example.com", "it.open.staff@studymatch.com", "Staff Open Test",
        passwordEncoder.encode(PASSWORD));
    staff.setMustChangePassword(false);
    staffRepository.save(staff);
    MockHttpSession staffSession = loginAs(staff.getInstitutionalEmail());

    // Act + Assert
    mockMvc.perform(get("/chats/" + withRui.getId() + "/messages").session(staffSession))
        .andExpect(status().isForbidden());
    mockMvc.perform(put("/chats/" + withRui.getId() + "/read").with(csrf()).session(staffSession))
        .andExpect(status().isForbidden());
  }

}
