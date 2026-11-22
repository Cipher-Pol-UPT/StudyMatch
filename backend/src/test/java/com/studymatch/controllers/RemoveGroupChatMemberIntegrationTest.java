package com.studymatch.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import com.studymatch.repositories.DegreeRepository;
import com.studymatch.repositories.MessageRepository;
import com.studymatch.repositories.StudentRepository;

import jakarta.persistence.EntityManager;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RemoveGroupChatMemberIntegrationTest {

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
  private EntityManager entityManager;

  @Autowired
  private PasswordEncoder passwordEncoder;

  private String passwordHash;
  private Degree degree;
  private Student ana;
  private Student rui;
  private Student eva;
  private CustomChat group;
  private MockHttpSession anaSession;

  // ana owns a group chat with rui and eva (rolled back after each test)
  @BeforeEach
  void setUp() throws Exception {
    passwordHash = passwordEncoder.encode(PASSWORD);
    degree = degreeRepository.save(new Degree("IT Remove Member Degree", 3));
    ana = saveStudent("it.removemember.ana@example.com", "Ana Remove Test");
    rui = saveStudent("it.removemember.rui@example.com", "Rui Remove Test");
    eva = saveStudent("it.removemember.eva@example.com", "Eva Remove Test");
    group = chatRepository.save(new CustomChat("Study group", null, ana, List.of(rui, eva)));
    anaSession = loginAs(ana.getInstitutionalEmail());
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

  private ResultActions remove(MockHttpSession session, Long chatId, Long number) throws Exception {
    return mockMvc.perform(delete("/chats/" + chatId + "/members/" + number).with(csrf()).session(session));
  }

  @Test
  @DisplayName("The owner removes a member and gets the members that are left")
  void ownerRemoves() throws Exception {
    // Act + Assert
    remove(anaSession, group.getId(), rui.getNumber())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[0].number").value(ana.getNumber()))
        .andExpect(jsonPath("$[1].number").value(eva.getNumber()));
  }

  @Test
  @DisplayName("Removing someone from the middle keeps everyone after them, in order")
  void removeFromTheMiddle() throws Exception {
    // Arrange - the list shifts up a place after rui
    Student zeca = saveStudent("it.removemember.zeca@example.com", "Zeca Remove Test");
    Student lia = saveStudent("it.removemember.lia@example.com", "Lia Remove Test");
    group.addMembers(List.of(zeca, lia));
    chatRepository.saveAndFlush(group);

    // Act + Assert
    remove(anaSession, group.getId(), rui.getNumber())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(4))
        .andExpect(jsonPath("$[1].number").value(eva.getNumber()))
        .andExpect(jsonPath("$[2].number").value(zeca.getNumber()))
        .andExpect(jsonPath("$[3].number").value(lia.getNumber()));
  }

  @Test
  @DisplayName("The removed member loses the chat and its history straight away (GR8)")
  void removedLosesAccess() throws Exception {
    // Arrange
    messageRepository.save(new Message(group, ana, "hi all"));
    MockHttpSession ruiSession = loginAs(rui.getInstitutionalEmail());
    remove(anaSession, group.getId(), rui.getNumber()).andExpect(status().isOk());

    // Act + Assert
    mockMvc.perform(get("/chats/").session(ruiSession))
        .andExpect(jsonPath("$.length()").value(0));
    mockMvc.perform(get("/chats/" + group.getId() + "/messages").session(ruiSession))
        .andExpect(status().isNotFound());
    mockMvc.perform(post("/chats/" + group.getId() + "/messages").with(csrf()).session(ruiSession)
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"text\":\"still here?\"}"))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("Someone removed can be added again, at the end, with the history back")
  void removedCanBeAddedAgain() throws Exception {
    // Arrange
    messageRepository.save(new Message(group, ana, "hi all"));
    remove(anaSession, group.getId(), rui.getNumber()).andExpect(status().isOk());
    // two requests commit on their own, in one test transaction they'd go in a single flush as a reshuffle
    entityManager.flush();
    MockHttpSession ruiSession = loginAs(rui.getInstitutionalEmail());

    // Act
    mockMvc.perform(post("/chats/" + group.getId() + "/members").with(csrf()).session(anaSession)
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"memberNumbers\":[" + rui.getNumber() + "]}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[2].number").value(rui.getNumber()));

    // Assert
    mockMvc.perform(get("/chats/" + group.getId() + "/messages").session(ruiSession))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.messages[0].text").value("hi all"));
  }

  @Test
  @DisplayName("The owner can remove people until only they are left, the minimum is only for creating")
  void belowThreeAllowed() throws Exception {
    // Act
    remove(anaSession, group.getId(), rui.getNumber()).andExpect(status().isOk());

    // Assert
    remove(anaSession, group.getId(), eva.getNumber())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1));
  }

  @Test
  @DisplayName("Down to the owner, they can still add one person, the 3 is only for creating")
  void smallChatCanGrowAgain() throws Exception {
    // Arrange - down to ana
    remove(anaSession, group.getId(), rui.getNumber()).andExpect(status().isOk());
    remove(anaSession, group.getId(), eva.getNumber()).andExpect(status().isOk());
    entityManager.flush();
    Student zeca = saveStudent("it.removemember.zeca@example.com", "Zeca Remove Test");

    // Act + Assert
    mockMvc.perform(post("/chats/" + group.getId() + "/members").with(csrf()).session(anaSession)
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"memberNumbers\":[" + zeca.getNumber() + "]}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2));
  }

  @Test
  @DisplayName("Removing someone who isn't in it changes nothing")
  void strangerChangesNothing() throws Exception {
    // Arrange
    Student zeca = saveStudent("it.removemember.zeca@example.com", "Zeca Remove Test");

    // Act + Assert
    remove(anaSession, group.getId(), zeca.getNumber())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(3));
  }

  @Test
  @DisplayName("A member who isn't the owner can't remove anyone: NOT_CHAT_OWNER")
  void notOwnerForbidden() throws Exception {
    // Arrange
    MockHttpSession ruiSession = loginAs(rui.getInstitutionalEmail());

    // Act + Assert
    remove(ruiSession, group.getId(), eva.getNumber())
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("NOT_CHAT_OWNER"));
  }

  @Test
  @DisplayName("The owner can't remove themselves: CANNOT_REMOVE_YOURSELF")
  void ownerCannotRemoveThemselves() throws Exception {
    // Act + Assert
    remove(anaSession, group.getId(), ana.getNumber())
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("CANNOT_REMOVE_YOURSELF"));
  }

  @Test
  @DisplayName("Nobody is removed from a DM: CHAT_MEMBERS_FIXED")
  void directChatFixed() throws Exception {
    // Arrange
    DirectChat direct = chatRepository.save(new DirectChat(ana, rui));

    // Act + Assert
    remove(anaSession, direct.getId(), rui.getNumber())
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CHAT_MEMBERS_FIXED"));
  }

  @Test
  @DisplayName("Someone who isn't in the group chat gets NOT_FOUND (GR8)")
  void nonMemberNotFound() throws Exception {
    // Arrange
    Student zeca = saveStudent("it.removemember.zeca@example.com", "Zeca Remove Test");
    MockHttpSession zecaSession = loginAs(zeca.getInstitutionalEmail());

    // Act + Assert
    remove(zecaSession, group.getId(), rui.getNumber())
        .andExpect(status().isNotFound());
  }

}
