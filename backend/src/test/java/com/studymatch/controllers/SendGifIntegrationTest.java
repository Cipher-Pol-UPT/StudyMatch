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

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.studymatch.dtos.MessageDTO;
import com.studymatch.events.ChatEvent;
import com.studymatch.events.UserChatEvent;
import com.studymatch.models.Degree;
import com.studymatch.models.DirectChat;
import com.studymatch.models.Message;
import com.studymatch.models.PersonalityProfile;
import com.studymatch.models.Student;
import com.studymatch.models.UserBlock;
import com.studymatch.repositories.ChatRepository;
import com.studymatch.repositories.DegreeRepository;
import com.studymatch.repositories.MessageRepository;
import com.studymatch.repositories.StudentRepository;
import com.studymatch.repositories.UserBlockRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@RecordApplicationEvents
@Transactional
class SendGifIntegrationTest {

  private static final String PASSWORD = "Test1234";
  private static final String GIF = "xT4uQulxzV39haRFjG";

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
  private UserBlockRepository userBlockRepository;

  @Autowired
  private PasswordEncoder passwordEncoder;

  @Autowired
  private ApplicationEvents events;

  private Degree degree;
  private Student ana;
  private Student rui;
  private Student eva;
  private DirectChat chat;
  private MockHttpSession anaSession;
  private MockHttpSession ruiSession;

  // Ana has a DM with Rui, Eva isn't in it (rolled back after each test)
  @BeforeEach
  void setUp() throws Exception {
    degree = degreeRepository.save(new Degree("IT Gif Degree", 3));
    ana = saveStudent("it.gif.ana@example.com", "Ana Gif Test");
    rui = saveStudent("it.gif.rui@example.com", "Rui Gif Test");
    eva = saveStudent("it.gif.eva@example.com", "Eva Gif Test");
    chat = chatRepository.save(new DirectChat(ana, rui));
    anaSession = loginAs(ana);
    ruiSession = loginAs(rui);
  }

  private Student saveStudent(String personalEmail, String name) {
    Student student = new Student(personalEmail, name, passwordEncoder.encode(PASSWORD), degree, 1);
    student.setMustChangePassword(false);
    student.setPersonalityProfile(new PersonalityProfile(0.5, 0.5, 0.5, 0.5, 0.5));
    student = studentRepository.save(student);
    student.generateInstitutionalEmail("studymatch.com");
    return studentRepository.save(student);
  }

  private MockHttpSession loginAs(Student student) throws Exception {
    MvcResult result = mockMvc.perform(post("/users/login").with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"email\":\"" + student.getInstitutionalEmail() + "\",\"password\":\"" + PASSWORD + "\"}"))
        .andReturn();
    assertEquals(200, result.getResponse().getStatus());
    return (MockHttpSession) result.getRequest().getSession(false);
  }

  private String messages() {
    return "/chats/" + chat.getId() + "/messages";
  }

  private ResultActions send(String url, MockHttpSession session, String body) throws Exception {
    return mockMvc.perform(post(url).session(session).with(csrf())
        .contentType(MediaType.APPLICATION_JSON).content(body));
  }

  private ResultActions edit(Message message, MockHttpSession session, String text) throws Exception {
    return mockMvc.perform(put(messages() + "/" + message.getId()).session(session).with(csrf())
        .contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"" + text + "\"}"));
  }

  private static String gif(String id) {
    return "{\"gifId\":\"" + id + "\"}";
  }

  private Message savedGif() {
    return messageRepository.saveAndFlush(new Message(chat, ana, "", null, GIF));
  }

  // sending

  @Test
  @DisplayName("A member sends a GIF: 201 with its id and no text, and the other member sees it in the chat")
  void sendsGif() throws Exception {
    // Act
    send(messages(), anaSession, gif(GIF))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.gifId").value(GIF))
        .andExpect(jsonPath("$.text").value(""))
        .andExpect(jsonPath("$.attachments").isEmpty());

    // Assert
    mockMvc.perform(get(messages()).session(ruiSession))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.messages[0].gifId").value(GIF))
        .andExpect(jsonPath("$.messages[0].text").value(""));
  }

  @Test
  @DisplayName("A text message has no GIF")
  void textHasNoGif() throws Exception {
    // Act + Assert
    send(messages(), anaSession, "{\"text\":\"hi\"}")
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.gifId").value(Matchers.nullValue()));
  }

  @Test
  @DisplayName("The first message of a DM can be a GIF")
  void firstMessage() throws Exception {
    // Act + Assert
    send("/chats/direct/" + eva.getNumber() + "/messages", anaSession, gif(GIF))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.gifId").value(GIF));
  }

  @Test
  @DisplayName("The open chat and both chat lists get the GIF live")
  void isPushed() throws Exception {
    // Act
    send(messages(), anaSession, gif(GIF)).andExpect(status().isCreated());

    // Assert
    assertEquals(1, events.stream(ChatEvent.class)
        .filter(event -> event.payload() instanceof MessageDTO message && GIF.equals(message.gifId())).count());
    assertEquals(2, events.stream(UserChatEvent.class)
        .filter(event -> event.payload() instanceof MessageDTO message && GIF.equals(message.gifId())).count());
  }

  @Test
  @DisplayName("The chat list's preview is the GIF, and it counts as unread")
  void chatListPreview() throws Exception {
    // Arrange
    savedGif();

    // Act + Assert
    mockMvc.perform(get("/chats/").session(ruiSession))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].lastMessage.gifId").value(GIF))
        .andExpect(jsonPath("$[0].unread").value(1));
  }

  // refusing

  @ParameterizedTest(name = "{0}")
  @ValueSource(strings = {
      "{}",
      "{\"text\":\"\",\"gifId\":null}",
      "{\"text\":\"hi\",\"gifId\":\"abc123\"}",
      "{\"gifId\":\"\"}",
      "{\"gifId\":\"../abc\"}",
      "{\"gifId\":\"abc,123\"}" })
  @DisplayName("Both, neither or a bad id is VALIDATION_FAILED, nothing is saved and no DM is created")
  void refused(String body) throws Exception {
    // Arrange
    long messagesBefore = messageRepository.count();
    long chatsBefore = chatRepository.count();

    // Act
    for (String url : new String[] { messages(), "/chats/direct/" + eva.getNumber() + "/messages" }) {
      send(url, anaSession, body)
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    // Assert
    assertEquals(messagesBefore, messageRepository.count());
    assertEquals(chatsBefore, chatRepository.count());
  }

  @Test
  @DisplayName("A bad id names its field")
  void badIdNamesItsField() throws Exception {
    // Act + Assert
    send(messages(), anaSession, gif("abc/123"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors[0].field").value("gifId"))
        .andExpect(jsonPath("$.fieldErrors[0].code").value("PATTERN"));
  }

  @Test
  @DisplayName("Someone who isn't in the chat gets 404 NOT_FOUND (GR8)")
  void notAMember() throws Exception {
    // Act + Assert
    send(messages(), loginAs(eva), gif(GIF))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
  }

  @Test
  @DisplayName("A GIF to someone who blocked the sender looks sent but never reaches them, not even after unblocking (GR9)")
  void blockedSender() throws Exception {
    // Arrange
    UserBlock block = userBlockRepository.saveAndFlush(new UserBlock(rui, ana));

    // Act
    send(messages(), anaSession, gif(GIF)).andExpect(status().isCreated());

    // Assert
    userBlockRepository.delete(block);
    userBlockRepository.flush();
    mockMvc.perform(get(messages()).session(ruiSession))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.messages").isEmpty());
    assertEquals(0, events.stream(ChatEvent.class).count());
  }

  // with the rest

  @Test
  @DisplayName("A reply to a GIF quotes its id, and a GIF can be the reply")
  void replies() throws Exception {
    // Arrange
    Message original = savedGif();

    // Act + Assert
    send(messages(), ruiSession, "{\"gifId\":\"abc123\",\"replyToId\":" + original.getId() + "}")
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.gifId").value("abc123"))
        .andExpect(jsonPath("$.replyTo.gifId").value(GIF))
        .andExpect(jsonPath("$.replyTo.text").value(""));
  }

  @ParameterizedTest(name = "\"{0}\"")
  @ValueSource(strings = { "", "hi" })
  @DisplayName("The author can't edit a GIF: 409 MESSAGE_NOT_EDITABLE (GR10)")
  void cantBeEdited(String text) throws Exception {
    // Arrange
    Message message = savedGif();

    // Act
    edit(message, anaSession, text)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("MESSAGE_NOT_EDITABLE"));

    // Assert
    assertEquals("", message.getText());
    assertFalse(message.isEdited());
  }

  @Test
  @DisplayName("Editing someone else's GIF is still NOT_MESSAGE_AUTHOR")
  void someoneElsesGif() throws Exception {
    // Act + Assert
    edit(savedGif(), ruiSession, "hi")
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("NOT_MESSAGE_AUTHOR"));
  }

  @Test
  @DisplayName("A GIF can be reacted to")
  void canBeReactedTo() throws Exception {
    // Act + Assert
    mockMvc.perform(put(messages() + "/" + savedGif().getId() + "/reaction").session(ruiSession).with(csrf())
        .contentType(MediaType.APPLICATION_JSON).content("{\"emoji\":\"👍\"}"))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("The author deletes a GIF like any other message")
  void canBeDeleted() throws Exception {
    // Arrange
    Message message = savedGif();

    // Act
    mockMvc.perform(delete(messages() + "/" + message.getId()).session(anaSession).with(csrf()))
        .andExpect(status().isNoContent());

    // Assert
    assertTrue(messageRepository.findById(message.getId()).isEmpty());
  }

  @Test
  @DisplayName("Search never finds a GIF, not even by its id")
  void notSearched() throws Exception {
    // Arrange
    savedGif();

    // Act + Assert
    mockMvc.perform(get(messages() + "/search").param("text", GIF).session(anaSession))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.messages").isEmpty());
  }

}
