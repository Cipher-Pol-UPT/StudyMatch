package com.studymatch.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

import com.studymatch.models.Degree;
import com.studymatch.models.DirectChat;
import com.studymatch.models.Message;
import com.studymatch.models.PersonalityProfile;
import com.studymatch.models.Staff;
import com.studymatch.models.Student;
import com.studymatch.models.Teacher;
import com.studymatch.models.User;
import com.studymatch.repositories.ChatRepository;
import com.studymatch.repositories.DegreeRepository;
import com.studymatch.repositories.MessageRepository;
import com.studymatch.repositories.StaffRepository;
import com.studymatch.repositories.StudentRepository;
import com.studymatch.repositories.TeacherRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class DeleteMessageIntegrationTest {

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
  private PasswordEncoder passwordEncoder;

  private Degree degree;
  private Student ana;
  private Student rui;
  private Student eva;
  private DirectChat withRui;
  private Message anasMessage;
  private MockHttpSession anaSession;

  // ana and rui share a DM with one message from ana, eva isn't in it (rolled back after each test)
  @BeforeEach
  void setUp() throws Exception {
    degree = degreeRepository.save(new Degree("IT Delete Message Degree", 3));
    ana = saveStudent("it.delete.ana@example.com", "Ana Delete Test");
    rui = saveStudent("it.delete.rui@example.com", "Rui Delete Test");
    eva = saveStudent("it.delete.eva@example.com", "Eva Delete Test");
    withRui = chatRepository.save(new DirectChat(ana, rui));
    anasMessage = message(withRui, ana, "see you at 10");
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

  private ResultActions deleteMessage(Long chatId, Long messageId, MockHttpSession session) throws Exception {
    return mockMvc.perform(delete("/chats/" + chatId + "/messages/" + messageId).session(session).with(csrf()));
  }

  // deleting

  @Test
  @DisplayName("The author deletes the message and nothing of it is kept (GR10)")
  void authorDeletes() throws Exception {
    // Act
    deleteMessage(withRui.getId(), anasMessage.getId(), anaSession)
        .andExpect(status().isNoContent());

    // Assert
    assertFalse(messageRepository.existsById(anasMessage.getId()));
  }

  @Test
  @DisplayName("Opening the chat afterwards shows the other messages without it, no placeholder")
  void openingLeavesItOut() throws Exception {
    // Arrange
    message(withRui, rui, "ok");

    // Act
    deleteMessage(withRui.getId(), anasMessage.getId(), anaSession).andExpect(status().isNoContent());

    // Assert
    mockMvc.perform(get("/chats/" + withRui.getId() + "/messages").session(anaSession))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.messages.length()").value(1))
        .andExpect(jsonPath("$.messages[0].text").value("ok"));
  }

  @Test
  @DisplayName("An unread message deleted stops counting, in the chat list and in the side menu's total")
  void unreadGoesDown() throws Exception {
    // Arrange
    Message second = message(withRui, ana, "or 11");
    MockHttpSession ruiSession = loginAs(rui.getInstitutionalEmail());

    // Act
    deleteMessage(withRui.getId(), second.getId(), anaSession).andExpect(status().isNoContent());

    // Assert
    mockMvc.perform(get("/chats/").session(ruiSession))
        .andExpect(jsonPath("$[0].unread").value(1));
    mockMvc.perform(get("/chats/unread-count").session(ruiSession))
        .andExpect(content().string("1"));
  }

  @Test
  @DisplayName("Deleting the latest message puts the one before it back as the chat list's preview")
  void previewFallsBack() throws Exception {
    // Arrange
    Message latest = message(withRui, ana, "or 11");

    // Act
    deleteMessage(withRui.getId(), latest.getId(), anaSession).andExpect(status().isNoContent());

    // Assert
    mockMvc.perform(get("/chats/").session(anaSession))
        .andExpect(jsonPath("$[0].lastMessage.id").value(anasMessage.getId()))
        .andExpect(jsonPath("$[0].lastMessage.text").value("see you at 10"));
  }

  @Test
  @DisplayName("Deleting the only message of a DM takes it off both chat lists, as if it never started")
  void onlyMessageEmptiesTheList() throws Exception {
    // Act
    deleteMessage(withRui.getId(), anasMessage.getId(), anaSession).andExpect(status().isNoContent());

    // Assert
    mockMvc.perform(get("/chats/").session(anaSession))
        .andExpect(jsonPath("$.length()").value(0));
    mockMvc.perform(get("/chats/").session(loginAs(rui.getInstitutionalEmail())))
        .andExpect(jsonPath("$.length()").value(0));
  }

  @Test
  @DisplayName("Teachers delete their messages too")
  void teacherDeletes() throws Exception {
    // Arrange
    Teacher teo = new Teacher("it.delete.teo@example.com", "it.delete.teo@studymatch.com", "Teo Delete Test",
        passwordEncoder.encode(PASSWORD));
    teo.setMustChangePassword(false);
    teo = teacherRepository.save(teo);
    DirectChat withTeo = chatRepository.save(new DirectChat(teo, ana));
    Message teosMessage = message(withTeo, teo, "class is moved");

    // Act
    deleteMessage(withTeo.getId(), teosMessage.getId(), loginAs(teo.getInstitutionalEmail()))
        .andExpect(status().isNoContent());

    // Assert
    assertFalse(messageRepository.existsById(teosMessage.getId()));
  }

  @Test
  @DisplayName("Deleting the same message twice is 404 the second time")
  void deleteTwice() throws Exception {
    // Arrange
    deleteMessage(withRui.getId(), anasMessage.getId(), anaSession).andExpect(status().isNoContent());

    // Act + Assert
    deleteMessage(withRui.getId(), anasMessage.getId(), anaSession)
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
  }

  // who can delete

  @Test
  @DisplayName("The other member can't delete someone else's message: 403 NOT_MESSAGE_AUTHOR")
  void notTheAuthor() throws Exception {
    // Act
    deleteMessage(withRui.getId(), anasMessage.getId(), loginAs(rui.getInstitutionalEmail()))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("NOT_MESSAGE_AUTHOR"));

    // Assert
    assertTrue(messageRepository.existsById(anasMessage.getId()));
  }

  @Test
  @DisplayName("Someone outside the chat gets 404, like for any other chat they're not in")
  void notAMember() throws Exception {
    // Act
    deleteMessage(withRui.getId(), anasMessage.getId(), loginAs(eva.getInstitutionalEmail()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));

    // Assert
    assertTrue(messageRepository.existsById(anasMessage.getId()));
  }

  @Test
  @DisplayName("A message id that doesn't exist is 404")
  void missingMessage() throws Exception {
    // Act + Assert
    deleteMessage(withRui.getId(), anasMessage.getId() + 1000, anaSession)
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
  }

  @Test
  @DisplayName("Your message from another chat, addressed through this one, is 404 and stays")
  void messageFromAnotherChat() throws Exception {
    // Arrange
    DirectChat withEva = chatRepository.save(new DirectChat(ana, eva));
    Message toEva = message(withEva, ana, "hi eva");

    // Act
    deleteMessage(withRui.getId(), toEva.getId(), anaSession)
        .andExpect(status().isNotFound());

    // Assert
    assertTrue(messageRepository.existsById(toEva.getId()));
  }

  @Test
  @DisplayName("A chat that doesn't exist is 404")
  void missingChat() throws Exception {
    // Act + Assert
    deleteMessage(withRui.getId() + 1000, anasMessage.getId(), anaSession)
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("Staff don't have chats: 403")
  void staffForbidden() throws Exception {
    // Arrange
    Staff staff = new Staff("it.delete.staff@example.com", "it.delete.staff@studymatch.com", "Staff Delete Test",
        passwordEncoder.encode(PASSWORD));
    staff.setMustChangePassword(false);
    staffRepository.save(staff);

    // Act
    deleteMessage(withRui.getId(), anasMessage.getId(), loginAs(staff.getInstitutionalEmail()))
        .andExpect(status().isForbidden());

    // Assert
    assertTrue(messageRepository.existsById(anasMessage.getId()));
  }

}
