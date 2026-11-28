package com.studymatch.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;
import com.studymatch.enums.NotificationType;
import com.studymatch.models.Chat;
import com.studymatch.models.CustomChat;
import com.studymatch.models.Degree;
import com.studymatch.models.DirectChat;
import com.studymatch.models.Notification;
import com.studymatch.models.PersonalityProfile;
import com.studymatch.models.Student;
import com.studymatch.models.User;
import com.studymatch.repositories.ChatRepository;
import com.studymatch.repositories.DegreeRepository;
import com.studymatch.repositories.NotificationRepository;
import com.studymatch.repositories.StudentRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class MentionSomeoneIntegrationTest {

  private static final String PASSWORD = "Test1234";
  private static final byte[] PNG = HexFormat.of().parseHex("89504e470d0a1a0a00");

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

  // ana, rui and eva are in a group chat, ana and rui share a DM, zeca is in neither (rolled back after each test)
  @BeforeEach
  void setUp() throws Exception {
    degree = degreeRepository.save(new Degree("IT Mention Degree", 3));
    ana = saveStudent("it.mention.ana@example.com", "Ana Mention Test");
    rui = saveStudent("it.mention.rui@example.com", "Rui Mention Test");
    eva = saveStudent("it.mention.eva@example.com", "Eva Mention Test");
    zeca = saveStudent("it.mention.zeca@example.com", "Zeca Mention Test");
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

  private ResultActions send(Chat chat, MockHttpSession session, String text, User... mentioned) throws Exception {
    String numbers = Arrays.stream(mentioned).map(user -> String.valueOf(user.getNumber()))
        .collect(Collectors.joining(","));
    return mockMvc.perform(post("/chats/" + chat.getId() + "/messages").with(csrf()).session(session)
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"text\":\"" + text + "\",\"mentions\":[" + numbers + "]}"));
  }

  private long sentId(ResultActions sent) throws Exception {
    Number id = JsonPath.read(sent.andReturn().getResponse().getContentAsString(), "$.id");
    return id.longValue();
  }

  private void setLevel(Chat chat, MockHttpSession session, String level) throws Exception {
    mockMvc.perform(put("/chats/" + chat.getId() + "/notification-level").with(csrf()).session(session)
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"level\":\"" + level + "\"}"))
        .andExpect(status().isNoContent());
  }

  private List<Notification> notificationsOf(User user, Chat chat) {
    return notificationRepository.findAll().stream()
        .filter(notification -> notification.getRecipient().getNumber().equals(user.getNumber()))
        .filter(notification -> notification.getType() == NotificationType.CHAT_MESSAGES)
        .filter(notification -> notification.getTargetId().equals(chat.getId()))
        .toList();
  }

  // saving the mention

  @Test
  @DisplayName("A mention of a member is saved with the message and comes back with it, and when the chat opens")
  void mentionSaved() throws Exception {
    // Act + Assert
    send(group, ruiSession, "@Ana Mention Test can you check this", ana)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.mentions.length()").value(1))
        .andExpect(jsonPath("$.mentions[0].number").value(ana.getNumber()))
        .andExpect(jsonPath("$.mentions[0].name").value("Ana Mention Test"));
    mockMvc.perform(get("/chats/" + group.getId() + "/messages").session(anaSession))
        .andExpect(jsonPath("$.messages[0].mentions[0].number").value(ana.getNumber()));
  }

  @Test
  @DisplayName("Two members can be mentioned in one message, they come back by name")
  void twoMentions() throws Exception {
    // Act + Assert
    send(group, ruiSession, "@Eva Mention Test @Ana Mention Test meeting?", eva, ana)
        .andExpect(jsonPath("$.mentions.length()").value(2))
        .andExpect(jsonPath("$.mentions[0].number").value(ana.getNumber()))
        .andExpect(jsonPath("$.mentions[1].number").value(eva.getNumber()));
  }

  @Test
  @DisplayName("Someone who isn't a member isn't mentioned, and the text is still sent")
  void notMember() throws Exception {
    // Act + Assert
    send(group, ruiSession, "@Zeca Mention Test hi", zeca)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.text").value("@Zeca Mention Test hi"))
        .andExpect(jsonPath("$.mentions.length()").value(0));
  }

  @Test
  @DisplayName("Mentioning yourself saves no mention")
  void mentionYourself() throws Exception {
    // Act + Assert
    send(group, ruiSession, "@Rui Mention Test note to self", rui)
        .andExpect(jsonPath("$.mentions.length()").value(0));
  }

  @Test
  @DisplayName("A DM has no mentions, the other person isn't saved as one")
  void directChat() throws Exception {
    // Act + Assert
    send(withRui, ruiSession, "@Ana Mention Test hi", ana)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.mentions.length()").value(0));
  }

  @Test
  @DisplayName("A message with files can mention members too")
  void withFiles() throws Exception {
    // Act + Assert
    mockMvc.perform(multipart("/chats/" + group.getId() + "/messages")
        .file(new MockMultipartFile("files", "notes.png", "image/png", PNG))
        .param("text", "@Ana Mention Test the notes")
        .param("mentions", String.valueOf(ana.getNumber()))
        .with(csrf()).session(ruiSession))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.mentions[0].number").value(ana.getNumber()));
  }

  @Test
  @DisplayName("20 mentions are fine, 21 is 400 VALIDATION_FAILED")
  void tooManyMentions() throws Exception {
    // Arrange
    String twenty = LongStream.rangeClosed(1, 20).mapToObj(String::valueOf).collect(Collectors.joining(","));
    String twentyOne = twenty + ",21";

    // Act + Assert
    mockMvc.perform(post("/chats/" + group.getId() + "/messages").with(csrf()).session(ruiSession)
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"text\":\"hi\",\"mentions\":[" + twenty + "]}"))
        .andExpect(status().isCreated());
    mockMvc.perform(post("/chats/" + group.getId() + "/messages").with(csrf()).session(ruiSession)
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"text\":\"hi\",\"mentions\":[" + twentyOne + "]}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
  }

  @Test
  @DisplayName("A message with mentions can be deleted, they go with it")
  void deleteMentioning() throws Exception {
    // Arrange
    long id = sentId(send(group, ruiSession, "@Ana Mention Test oops", ana));

    // Act + Assert
    mockMvc.perform(delete("/chats/" + group.getId() + "/messages/" + id).with(csrf()).session(ruiSession))
        .andExpect(status().isNoContent());
    mockMvc.perform(get("/chats/" + group.getId() + "/messages").session(anaSession))
        .andExpect(jsonPath("$.messages.length()").value(0));
  }

  @Test
  @DisplayName("The last member leaving deletes a chat whose messages mention someone, mentions and all")
  void lastOneLeaves() throws Exception {
    // Arrange
    send(group, ruiSession, "@Ana Mention Test bye", ana).andExpect(status().isCreated());
    MockHttpSession evaSession = loginAs(eva.getInstitutionalEmail());

    // Act + Assert
    for (MockHttpSession session : List.of(evaSession, ruiSession, anaSession)) {
      mockMvc.perform(post("/chats/" + group.getId() + "/leave").with(csrf()).session(session))
          .andExpect(status().isNoContent());
    }
    assertTrue(chatRepository.findById(group.getId()).isEmpty());
  }

  // notifying

  @Test
  @DisplayName("On Only @mentions a mention notifies, counting 1 message and 1 mention")
  void levelMentionsNotified() throws Exception {
    // Arrange
    setLevel(group, anaSession, "MENTIONS");

    // Act
    send(group, ruiSession, "@Ana Mention Test look", ana);

    // Assert
    List<Notification> notifications = notificationsOf(ana, group);
    assertEquals(1, notifications.size());
    assertEquals(1, notifications.getFirst().getParams().get("count"));
    assertEquals(1, notifications.getFirst().getParams().get("mentions"));
  }

  @Test
  @DisplayName("On Only @mentions the messages around a mention don't count, only the mention does")
  void levelMentionsSkipsOthers() throws Exception {
    // Arrange
    setLevel(group, anaSession, "MENTIONS");

    // Act
    send(group, ruiSession, "hi all");
    send(group, ruiSession, "@Ana Mention Test look", ana);
    send(group, ruiSession, "anyone?");

    // Assert
    assertEquals(1, notificationsOf(ana, group).getFirst().getParams().get("count"));
  }

  @Test
  @DisplayName("On None a mention doesn't notify either")
  void levelNoneMentioned() throws Exception {
    // Arrange
    setLevel(group, anaSession, "NONE");

    // Act
    send(group, ruiSession, "@Ana Mention Test look", ana);

    // Assert
    assertTrue(notificationsOf(ana, group).isEmpty());
  }

  @Test
  @DisplayName("On All every message counts and the mentions are counted apart, only for whoever was mentioned")
  void levelAllCountsMentions() throws Exception {
    // Act
    send(group, ruiSession, "hi all");
    send(group, ruiSession, "@Ana Mention Test look", ana);

    // Assert
    assertEquals(2, notificationsOf(ana, group).getFirst().getParams().get("count"));
    assertEquals(1, notificationsOf(ana, group).getFirst().getParams().get("mentions"));
    assertEquals(0, notificationsOf(eva, group).getFirst().getParams().get("mentions"));
  }

  @Test
  @DisplayName("Editing a message that mentions someone never notifies again (GR10)")
  void editNotifiesNoOne() throws Exception {
    // Arrange
    setLevel(group, anaSession, "MENTIONS");
    long id = sentId(send(group, ruiSession, "@Ana Mention Test look", ana));

    // Act
    mockMvc.perform(put("/chats/" + group.getId() + "/messages/" + id).with(csrf()).session(ruiSession)
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"text\":\"@Ana Mention Test look at this\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.mentions[0].number").value(ana.getNumber()));

    // Assert
    assertEquals(1, notificationsOf(ana, group).getFirst().getParams().get("count"));
  }

}
