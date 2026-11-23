package com.studymatch.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

import com.studymatch.models.Chat;
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
class ReplyMessageIntegrationTest {

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
  private DirectChat chat;
  private Message original;
  private MockHttpSession anaSession;
  private MockHttpSession ruiSession;

  // Rui wrote to Ana in their DM (rolled back after each test)
  @BeforeEach
  void setUp() throws Exception {
    passwordHash = passwordEncoder.encode(PASSWORD);
    degree = degreeRepository.save(new Degree("IT Reply Degree", 3));
    ana = saveStudent("it.reply.ana@example.com", "Ana Reply Test");
    rui = saveStudent("it.reply.rui@example.com", "Rui Reply Test");
    eva = saveStudent("it.reply.eva@example.com", "Eva Reply Test");
    chat = chatRepository.save(new DirectChat(ana, rui));
    original = messageRepository.save(new Message(chat, rui, "lunch?"));
    anaSession = loginAs(ana.getInstitutionalEmail());
    ruiSession = loginAs(rui.getInstitutionalEmail());
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

  private ResultActions reply(MockHttpSession session, Long chatId, Long replyToId, String text) throws Exception {
    return mockMvc.perform(post("/chats/" + chatId + "/messages").with(csrf()).session(session)
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"text\":\"" + text + "\",\"replyToId\":" + replyToId + "}"));
  }

  private ResultActions openChat(MockHttpSession session) throws Exception {
    // what's loaded here doesn't know what the database cleared
    entityManager.flush();
    entityManager.clear();
    return mockMvc.perform(get("/chats/" + chat.getId() + "/messages").session(session)).andExpect(status().isOk());
  }

  private Message saveReply(Chat in, Student author, String text, Message replyTo) {
    return messageRepository.saveAndFlush(new Message(in, author, text, replyTo));
  }

  @Test
  @DisplayName("A reply comes back with the quote, and so it does when the chat's opened")
  void replyHasTheQuote() throws Exception {
    // Act
    reply(anaSession, chat.getId(), original.getId(), "at one")
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.text").value("at one"))
        .andExpect(jsonPath("$.reply").value(true))
        .andExpect(jsonPath("$.replyTo.id").value(original.getId()))
        .andExpect(jsonPath("$.replyTo.text").value("lunch?"));

    // Assert
    openChat(ruiSession)
        .andExpect(jsonPath("$.messages[0].reply").value(false))
        .andExpect(jsonPath("$.messages[0].replyTo").isEmpty())
        .andExpect(jsonPath("$.messages[1].reply").value(true))
        .andExpect(jsonPath("$.messages[1].replyTo.author.number").value(rui.getNumber()))
        .andExpect(jsonPath("$.messages[1].replyTo.text").value("lunch?"));
  }

  @Test
  @DisplayName("Scrolling up and the chat list have the quote too")
  void olderPageAndListHaveTheQuote() throws Exception {
    // Arrange
    Message answer = saveReply(chat, ana, "at one", original);

    // Act + Assert
    openChat(ruiSession);
    mockMvc.perform(get("/chats/" + chat.getId() + "/messages?before=" + (answer.getId() + 1)).session(ruiSession))
        .andExpect(jsonPath("$.messages[1].replyTo.text").value("lunch?"));
    mockMvc.perform(get("/chats/").session(ruiSession))
        .andExpect(jsonPath("$[0].lastMessage.replyTo.text").value("lunch?"));
  }

  @Test
  @DisplayName("A reply to a reply quotes only the one it answers")
  void replyToAReply() throws Exception {
    // Arrange
    Message answer = saveReply(chat, ana, "at one", original);

    // Act + Assert
    reply(ruiSession, chat.getId(), answer.getId(), "ok")
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.replyTo.text").value("at one"))
        .andExpect(jsonPath("$.replyTo.replyTo").doesNotExist());
  }

  @Test
  @DisplayName("The DM route takes a reply too")
  void directRouteReplies() throws Exception {
    // Act + Assert
    mockMvc.perform(post("/chats/direct/" + rui.getNumber() + "/messages").with(csrf()).session(anaSession)
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"text\":\"at one\",\"replyToId\":" + original.getId() + "}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.replyTo.text").value("lunch?"));
  }

  @Test
  @DisplayName("Replying to a message from another chat is REPLIED_MESSAGE_NOT_FOUND and sends nothing")
  void otherChatRefused() throws Exception {
    // Arrange
    DirectChat anaAndEva = chatRepository.save(new DirectChat(ana, eva));
    long before = messageRepository.count();

    // Act
    reply(anaSession, anaAndEva.getId(), original.getId(), "at one")
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("REPLIED_MESSAGE_NOT_FOUND"));

    // Assert
    assertEquals(before, messageRepository.count());
  }

  @Test
  @DisplayName("Replying to a message that doesn't exist is REPLIED_MESSAGE_NOT_FOUND")
  void missingOriginal() throws Exception {
    // Act + Assert
    reply(anaSession, chat.getId(), -1L, "at one")
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("REPLIED_MESSAGE_NOT_FOUND"));
  }

  @Test
  @DisplayName("Someone who isn't in the chat gets NOT_FOUND, the chat's and not the message's (GR8)")
  void nonMember() throws Exception {
    // Act + Assert
    reply(loginAs(eva.getInstitutionalEmail()), chat.getId(), original.getId(), "hi")
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
  }

  @Test
  @DisplayName("Deleting the original keeps the reply, which still says it was one (GR10)")
  void originalDeleted() throws Exception {
    // Arrange
    Message answer = saveReply(chat, ana, "at one", original);
    entityManager.clear();

    // Act
    mockMvc.perform(delete("/chats/" + chat.getId() + "/messages/" + original.getId()).with(csrf())
        .session(ruiSession))
        .andExpect(status().isNoContent());

    // Assert
    openChat(anaSession)
        .andExpect(jsonPath("$.messages.length()").value(1))
        .andExpect(jsonPath("$.messages[0].id").value(answer.getId()))
        .andExpect(jsonPath("$.messages[0].reply").value(true))
        .andExpect(jsonPath("$.messages[0].replyTo").isEmpty());
  }

  @Test
  @DisplayName("Editing the original changes the quote, editing the reply keeps it")
  void edits() throws Exception {
    // Arrange
    Message answer = saveReply(chat, ana, "at one", original);

    // Act
    mockMvc.perform(put("/chats/" + chat.getId() + "/messages/" + original.getId()).with(csrf()).session(ruiSession)
        .contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"dinner?\"}"))
        .andExpect(status().isOk());
    mockMvc.perform(put("/chats/" + chat.getId() + "/messages/" + answer.getId()).with(csrf()).session(anaSession)
        .contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"at eight\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.replyTo.text").value("dinner?"));

    // Assert
    openChat(ruiSession)
        .andExpect(jsonPath("$.messages[1].text").value("at eight"))
        .andExpect(jsonPath("$.messages[1].replyTo.text").value("dinner?"));
  }

  @Test
  @DisplayName("Replying to a message hidden from you is REPLIED_MESSAGE_NOT_FOUND, like it doesn't exist (GR9)")
  void hiddenOriginalRefused() throws Exception {
    // Arrange
    original.hideFrom(ana);
    messageRepository.saveAndFlush(original);

    // Act + Assert
    reply(anaSession, chat.getId(), original.getId(), "at one")
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("REPLIED_MESSAGE_NOT_FOUND"));
  }

  @Test
  @DisplayName("A reply to your own hidden message shows its quote only to you (GR9)")
  void hiddenQuoteOnlyForItsAuthor() throws Exception {
    // Arrange - Ana had blocked Rui and unblocked since, what Rui sent meanwhile stays hidden
    original.hideFrom(ana);
    messageRepository.saveAndFlush(original);

    // Act
    reply(ruiSession, chat.getId(), original.getId(), "so?")
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.replyTo.text").value("lunch?"));

    // Assert
    openChat(anaSession)
        .andExpect(jsonPath("$.messages.length()").value(1))
        .andExpect(jsonPath("$.messages[0].reply").value(true))
        .andExpect(jsonPath("$.messages[0].replyTo").isEmpty());
    mockMvc.perform(get("/chats/").session(anaSession))
        .andExpect(jsonPath("$[0].lastMessage.text").value("so?"))
        .andExpect(jsonPath("$[0].lastMessage.replyTo").isEmpty());
    openChat(ruiSession)
        .andExpect(jsonPath("$.messages[1].replyTo.text").value("lunch?"));
  }

  @Test
  @DisplayName("A group chat with replies is deleted with them when the last one leaves")
  void lastOneOutWithReplies() throws Exception {
    // Arrange
    CustomChat group = chatRepository.save(new CustomChat("Study group", null, ana, List.of(rui, eva)));
    Message first = messageRepository.save(new Message(group, ana, "hi all"));
    saveReply(group, rui, "hi", first);
    for (MockHttpSession session : List.of(ruiSession, loginAs(eva.getInstitutionalEmail()))) {
      mockMvc.perform(post("/chats/" + group.getId() + "/leave").with(csrf()).session(session))
          .andExpect(status().isNoContent());
    }

    // Act
    mockMvc.perform(post("/chats/" + group.getId() + "/leave").with(csrf()).session(anaSession))
        .andExpect(status().isNoContent());

    // Assert
    entityManager.flush();
    entityManager.clear();
    assertTrue(chatRepository.findById(group.getId()).isEmpty());
    assertFalse(messageRepository.existsById(first.getId()));
  }

}
