package com.studymatch.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;

import org.hamcrest.Matchers;
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
class EditMessageIntegrationTest {

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
    degree = degreeRepository.save(new Degree("IT Edit Message Degree", 3));
    ana = saveStudent("it.edit.ana@example.com", "Ana Edit Test");
    rui = saveStudent("it.edit.rui@example.com", "Rui Edit Test");
    eva = saveStudent("it.edit.eva@example.com", "Eva Edit Test");
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

  private ResultActions edit(Long chatId, Long messageId, MockHttpSession session, String text) throws Exception {
    return mockMvc.perform(put("/chats/" + chatId + "/messages/" + messageId).session(session).with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"text\":\"" + text + "\"}"));
  }

  // editing

  @Test
  @DisplayName("The author changes the text and the message comes back with editedAt (GR10)")
  void authorEdits() throws Exception {
    // Act
    edit(withRui.getId(), anasMessage.getId(), anaSession, "see you at 11")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(anasMessage.getId()))
        .andExpect(jsonPath("$.text").value("see you at 11"))
        .andExpect(jsonPath("$.editedAt").isNotEmpty());

    // Assert
    Message saved = messageRepository.findById(anasMessage.getId()).orElseThrow();
    assertEquals("see you at 11", saved.getText());
    assertTrue(saved.isEdited());
  }

  @Test
  @DisplayName("Opening the chat afterwards shows the new text, edited, in the same place")
  void openingShowsTheEdit() throws Exception {
    // Arrange
    message(withRui, rui, "ok");
    edit(withRui.getId(), anasMessage.getId(), anaSession, "see you at 11").andExpect(status().isOk());

    // Act + Assert
    mockMvc.perform(get("/chats/" + withRui.getId() + "/messages").session(anaSession))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.messages[0].text").value("see you at 11"))
        .andExpect(jsonPath("$.messages[0].editedAt").isNotEmpty())
        .andExpect(jsonPath("$.messages[1].text").value("ok"))
        .andExpect(jsonPath("$.messages[1].editedAt").value(Matchers.nullValue()));
  }

  @Test
  @DisplayName("The new text has its spaces around trimmed, like when sending")
  void textIsTrimmed() throws Exception {
    // Act + Assert
    edit(withRui.getId(), anasMessage.getId(), anaSession, "  see you at 11  ")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.text").value("see you at 11"));
  }

  @Test
  @DisplayName("Saving the same text again doesn't mark the message as edited")
  void sameTextIsNoEdit() throws Exception {
    // Act
    edit(withRui.getId(), anasMessage.getId(), anaSession, " see you at 10 ")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.text").value("see you at 10"))
        .andExpect(jsonPath("$.editedAt").value(Matchers.nullValue()));

    // Assert
    assertFalse(messageRepository.findById(anasMessage.getId()).orElseThrow().isEdited());
  }

  @Test
  @DisplayName("A message can be edited again, editedAt moves to the latest edit")
  void editTwice() throws Exception {
    // Arrange
    edit(withRui.getId(), anasMessage.getId(), anaSession, "see you at 11").andExpect(status().isOk());
    LocalDateTime firstEdit = messageRepository.findById(anasMessage.getId()).orElseThrow().getEditedAt();
    Thread.sleep(5);

    // Act
    edit(withRui.getId(), anasMessage.getId(), anaSession, "see you at 12").andExpect(status().isOk());

    // Assert
    Message saved = messageRepository.findById(anasMessage.getId()).orElseThrow();
    assertEquals("see you at 12", saved.getText());
    assertTrue(saved.getEditedAt().isAfter(firstEdit));
  }

  @Test
  @DisplayName("Teachers edit their messages too")
  void teacherEdits() throws Exception {
    // Arrange
    Teacher teo = new Teacher("it.edit.teo@example.com", "it.edit.teo@studymatch.com", "Teo Edit Test",
        passwordEncoder.encode(PASSWORD));
    teo.setMustChangePassword(false);
    teo = teacherRepository.save(teo);
    DirectChat withTeo = chatRepository.save(new DirectChat(teo, ana));
    Message teosMessage = message(withTeo, teo, "class is moved");

    // Act + Assert
    edit(withTeo.getId(), teosMessage.getId(), loginAs(teo.getInstitutionalEmail()), "class is moved to room 2")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.text").value("class is moved to room 2"));
  }

  // the text rule (GR10)

  @Test
  @DisplayName("Empty text on a message without attachments is refused with MESSAGE_EMPTY and the old text stays")
  void blankText() throws Exception {
    // Act
    edit(withRui.getId(), anasMessage.getId(), anaSession, "   ")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("MESSAGE_EMPTY"));

    // Assert
    assertEquals("see you at 10", messageRepository.findById(anasMessage.getId()).orElseThrow().getText());
  }

  @Test
  @DisplayName("2000 characters is accepted, 2001 is refused with SIZE_MAX")
  void textLimit() throws Exception {
    // Act + Assert
    edit(withRui.getId(), anasMessage.getId(), anaSession, "a".repeat(2000))
        .andExpect(status().isOk());
    edit(withRui.getId(), anasMessage.getId(), anaSession, "b".repeat(2001))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors[0].code").value("SIZE_MAX"))
        .andExpect(jsonPath("$.fieldErrors[0].params.max").value(2000));
  }

  // who can edit

  @Test
  @DisplayName("The other member can't edit someone else's message: 403 NOT_MESSAGE_AUTHOR")
  void notTheAuthor() throws Exception {
    // Act
    edit(withRui.getId(), anasMessage.getId(), loginAs(rui.getInstitutionalEmail()), "hacked")
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("NOT_MESSAGE_AUTHOR"));

    // Assert
    Message saved = messageRepository.findById(anasMessage.getId()).orElseThrow();
    assertEquals("see you at 10", saved.getText());
    assertFalse(saved.isEdited());
  }

  @Test
  @DisplayName("Someone outside the chat gets 404, like for any other chat they're not in")
  void notAMember() throws Exception {
    // Act + Assert
    edit(withRui.getId(), anasMessage.getId(), loginAs(eva.getInstitutionalEmail()), "hacked")
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
  }

  @Test
  @DisplayName("A message id that doesn't exist is 404")
  void missingMessage() throws Exception {
    // Act + Assert
    edit(withRui.getId(), anasMessage.getId() + 1000, anaSession, "hello")
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
  }

  @Test
  @DisplayName("Your message from another chat, addressed through this one, is 404")
  void messageFromAnotherChat() throws Exception {
    // Arrange
    DirectChat withEva = chatRepository.save(new DirectChat(ana, eva));
    Message toEva = message(withEva, ana, "hi eva");

    // Act + Assert
    edit(withRui.getId(), toEva.getId(), anaSession, "hello")
        .andExpect(status().isNotFound());
    assertEquals("hi eva", messageRepository.findById(toEva.getId()).orElseThrow().getText());
  }

  @Test
  @DisplayName("A chat that doesn't exist is 404")
  void missingChat() throws Exception {
    // Act + Assert
    edit(withRui.getId() + 1000, anasMessage.getId(), anaSession, "hello")
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("Staff don't have chats: 403")
  void staffForbidden() throws Exception {
    // Arrange
    Staff staff = new Staff("it.edit.staff@example.com", "it.edit.staff@studymatch.com", "Staff Edit Test",
        passwordEncoder.encode(PASSWORD));
    staff.setMustChangePassword(false);
    staffRepository.save(staff);

    // Act + Assert
    edit(withRui.getId(), anasMessage.getId(), loginAs(staff.getInstitutionalEmail()), "hello")
        .andExpect(status().isForbidden());
  }

}
