package com.studymatch.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.studymatch.models.CustomChat;
import com.studymatch.models.Degree;
import com.studymatch.models.DirectChat;
import com.studymatch.models.Message;
import com.studymatch.models.PersonalityProfile;
import com.studymatch.models.Student;
import com.studymatch.repositories.ChatRepository;
import com.studymatch.repositories.ChatSettingsRepository;
import com.studymatch.repositories.DegreeRepository;
import com.studymatch.repositories.MessageRepository;
import com.studymatch.repositories.StudentRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class LeaveGroupChatIntegrationTest {

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
  private MessageRepository messageRepository;

  @Autowired
  private ChatSettingsRepository chatSettingsRepository;

  @Autowired
  private PasswordEncoder passwordEncoder;

  private String passwordHash;
  private Degree degree;
  private Student ana;
  private Student rui;
  private Student eva;
  private CustomChat group;
  private MockHttpSession anaSession;
  private MockHttpSession ruiSession;
  private MockHttpSession evaSession;

  // ana owns a group chat with rui and eva, who joined in that order (rolled back after each test)
  @BeforeEach
  void setUp() throws Exception {
    passwordHash = passwordEncoder.encode(PASSWORD);
    degree = degreeRepository.save(new Degree("IT Leave Chat Degree", 3));
    ana = saveStudent("it.leavechat.ana@example.com", "Ana Leave Test");
    rui = saveStudent("it.leavechat.rui@example.com", "Rui Leave Test");
    eva = saveStudent("it.leavechat.eva@example.com", "Eva Leave Test");
    group = chatRepository.save(new CustomChat("Study group", null, ana, List.of(rui, eva)));
    anaSession = loginAs(ana.getInstitutionalEmail());
    ruiSession = loginAs(rui.getInstitutionalEmail());
    evaSession = loginAs(eva.getInstitutionalEmail());
  }

  private Student saveStudent(String personalEmail, String name) {
    Student student = new Student(personalEmail, name, passwordHash, degree, 1);
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

  private ResultActions leave(MockHttpSession session, Long chatId) throws Exception {
    return mockMvc.perform(post("/chats/" + chatId + "/leave").with(csrf()).session(session));
  }

  @Test
  @DisplayName("A member leaves and loses the chat and its history, the others stay")
  void memberLeaves() throws Exception {
    // Arrange
    messageRepository.save(new Message(group, ana, "hi all"));

    // Act
    leave(ruiSession, group.getId()).andExpect(status().isNoContent());

    // Assert
    mockMvc.perform(get("/chats/").session(ruiSession))
        .andExpect(jsonPath("$.length()").value(0));
    mockMvc.perform(get("/chats/" + group.getId() + "/messages").session(ruiSession))
        .andExpect(status().isNotFound());
    mockMvc.perform(get("/chats/" + group.getId() + "/members").session(anaSession))
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[1].number").value(eva.getNumber()));
  }

  @Test
  @DisplayName("The owner leaving makes whoever's been there longest the owner, first in the list")
  void ownerHandsOver() throws Exception {
    // Act
    leave(anaSession, group.getId()).andExpect(status().isNoContent());

    // Assert - rui joined before eva, so rui owns it now
    mockMvc.perform(get("/chats/" + group.getId() + "/members").session(ruiSession))
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[0].number").value(rui.getNumber()));
    mockMvc.perform(delete("/chats/" + group.getId() + "/members/" + rui.getNumber()).with(csrf())
        .session(evaSession))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("NOT_CHAT_OWNER"));
    mockMvc.perform(delete("/chats/" + group.getId() + "/members/" + eva.getNumber()).with(csrf())
        .session(ruiSession))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("The last one out deletes the chat with its messages and everyone's settings")
  void lastOneOutDeletes() throws Exception {
    // Arrange - rui opened it before leaving, so rui's settings are there too
    messageRepository.save(new Message(group, ana, "hi all"));
    mockMvc.perform(put("/chats/" + group.getId() + "/read").with(csrf()).session(ruiSession))
        .andExpect(status().isNoContent());
    leave(ruiSession, group.getId()).andExpect(status().isNoContent());
    leave(evaSession, group.getId()).andExpect(status().isNoContent());

    // Act
    leave(anaSession, group.getId()).andExpect(status().isNoContent());

    // Assert
    assertTrue(chatRepository.findById(group.getId()).isEmpty());
    assertEquals(0, messageRepository.findAll().stream()
        .filter(message -> message.getChat().getId().equals(group.getId()))
        .count());
    assertEquals(0, chatSettingsRepository.findAll().stream()
        .filter(settings -> settings.getChat().getId().equals(group.getId()))
        .count());
    mockMvc.perform(get("/chats/" + group.getId() + "/messages").session(anaSession))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("Leaving a chat you're not in anymore is NOT_FOUND (GR8)")
  void leavingTwiceNotFound() throws Exception {
    // Arrange
    leave(ruiSession, group.getId()).andExpect(status().isNoContent());

    // Act + Assert
    leave(ruiSession, group.getId()).andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("Nobody leaves a DM: CHAT_MEMBERS_FIXED")
  void directChatFixed() throws Exception {
    // Arrange
    DirectChat direct = chatRepository.save(new DirectChat(ana, rui));

    // Act + Assert
    leave(anaSession, direct.getId())
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CHAT_MEMBERS_FIXED"));
  }

}
