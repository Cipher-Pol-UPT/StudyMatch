package com.studymatch.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Collections;
import java.util.List;

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
import com.studymatch.models.Staff;
import com.studymatch.models.Student;
import com.studymatch.models.UserBlock;
import com.studymatch.repositories.ChatRepository;
import com.studymatch.repositories.DegreeRepository;
import com.studymatch.repositories.MessageRepository;
import com.studymatch.repositories.ReactionRepository;
import com.studymatch.repositories.StaffRepository;
import com.studymatch.repositories.StudentRepository;
import com.studymatch.repositories.UserBlockRepository;

import jakarta.persistence.EntityManager;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ReactMessageIntegrationTest {

  private static final String PASSWORD = "Test1234";
  private static final String HEART = "❤️";
  private static final String LAUGH = "😂";

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private DegreeRepository degreeRepository;

  @Autowired
  private StudentRepository studentRepository;

  @Autowired
  private StaffRepository staffRepository;

  @Autowired
  private ChatRepository chatRepository;

  @Autowired
  private MessageRepository messageRepository;

  @Autowired
  private ReactionRepository reactionRepository;

  @Autowired
  private UserBlockRepository userBlockRepository;

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
  private Message message;
  private MockHttpSession anaSession;
  private MockHttpSession ruiSession;
  private MockHttpSession evaSession;

  // Rui wrote to Ana in their DM (rolled back after each test)
  @BeforeEach
  void setUp() throws Exception {
    passwordHash = passwordEncoder.encode(PASSWORD);
    degree = degreeRepository.save(new Degree("IT React Degree", 3));
    ana = saveStudent("it.react.ana@example.com", "Ana React Test");
    rui = saveStudent("it.react.rui@example.com", "Rui React Test");
    eva = saveStudent("it.react.eva@example.com", "Eva React Test");
    chat = chatRepository.save(new DirectChat(ana, rui));
    message = messageRepository.save(new Message(chat, rui, "lunch?"));
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

  private String url(Message reactedTo) {
    return "/chats/" + reactedTo.getChat().getId() + "/messages/" + reactedTo.getId() + "/reaction";
  }

  private ResultActions react(MockHttpSession session, Message reactedTo, String emoji) throws Exception {
    return mockMvc.perform(put(url(reactedTo)).with(csrf()).session(session)
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"emoji\":\"" + emoji + "\"}"));
  }

  private ResultActions removeReaction(MockHttpSession session, Message reactedTo) throws Exception {
    return mockMvc.perform(delete(url(reactedTo)).with(csrf()).session(session));
  }

  private ResultActions openChat(MockHttpSession session, Chat opened) throws Exception {
    // the insert skips what's loaded
    entityManager.flush();
    entityManager.clear();
    return mockMvc.perform(get("/chats/" + opened.getId() + "/messages").session(session)).andExpect(status().isOk());
  }

  @Test
  @DisplayName("A reaction comes back with who reacted, and both see it when they open the chat")
  void reactionShows() throws Exception {
    // Act
    react(anaSession, message, HEART)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.chatId").value(chat.getId()))
        .andExpect(jsonPath("$.messageId").value(message.getId()))
        .andExpect(jsonPath("$.user.number").value(ana.getNumber()))
        .andExpect(jsonPath("$.emoji").value(HEART));

    // Assert
    for (MockHttpSession session : List.of(anaSession, ruiSession)) {
      openChat(session, chat)
          .andExpect(jsonPath("$.reactions.length()").value(1))
          .andExpect(jsonPath("$.reactions[0].messageId").value(message.getId()))
          .andExpect(jsonPath("$.reactions[0].user.name").value("Ana React Test"))
          .andExpect(jsonPath("$.reactions[0].emoji").value(HEART));
    }
  }

  @Test
  @DisplayName("Another emoji replaces the one they had, and so does going back to the first")
  void anotherEmojiReplaces() throws Exception {
    // Arrange
    react(anaSession, message, HEART).andExpect(status().isOk());

    // Act + Assert
    react(anaSession, message, LAUGH).andExpect(status().isOk());
    openChat(ruiSession, chat)
        .andExpect(jsonPath("$.reactions.length()").value(1))
        .andExpect(jsonPath("$.reactions[0].emoji").value(LAUGH));
    react(anaSession, message, HEART).andExpect(status().isOk());
    openChat(ruiSession, chat)
        .andExpect(jsonPath("$.reactions.length()").value(1))
        .andExpect(jsonPath("$.reactions[0].emoji").value(HEART));
  }

  @Test
  @DisplayName("The same emoji again changes nothing")
  void sameEmojiAgain() throws Exception {
    // Arrange
    react(anaSession, message, HEART).andExpect(status().isOk());

    // Act
    react(anaSession, message, HEART).andExpect(status().isOk());

    // Assert
    openChat(ruiSession, chat).andExpect(jsonPath("$.reactions.length()").value(1));
  }

  @Test
  @DisplayName("Two people with the same emoji are two to count, the author reacting to their own included")
  void sameEmojiFromTwo() throws Exception {
    // Act
    react(anaSession, message, HEART).andExpect(status().isOk());
    react(ruiSession, message, HEART).andExpect(status().isOk());

    // Assert
    openChat(anaSession, chat)
        .andExpect(jsonPath("$.reactions.length()").value(2))
        .andExpect(jsonPath("$.reactions[0].user.number").value(ana.getNumber()))
        .andExpect(jsonPath("$.reactions[1].user.number").value(rui.getNumber()));
  }

  @Test
  @DisplayName("Taking it off removes only yours, and taking it off again changes nothing")
  void removeOnlyYours() throws Exception {
    // Arrange
    react(anaSession, message, HEART).andExpect(status().isOk());
    react(ruiSession, message, HEART).andExpect(status().isOk());

    // Act
    removeReaction(anaSession, message).andExpect(status().isNoContent());
    removeReaction(anaSession, message).andExpect(status().isNoContent());

    // Assert
    openChat(anaSession, chat)
        .andExpect(jsonPath("$.reactions.length()").value(1))
        .andExpect(jsonPath("$.reactions[0].user.number").value(rui.getNumber()));
  }

  @Test
  @DisplayName("Only the reactions to the page's messages come with it")
  void onlyThePagesReactions() throws Exception {
    // Arrange
    Message later = messageRepository.saveAndFlush(new Message(chat, rui, "or dinner?"));
    react(anaSession, message, HEART).andExpect(status().isOk());
    react(anaSession, later, LAUGH).andExpect(status().isOk());

    // Act + Assert
    openChat(anaSession, chat).andExpect(jsonPath("$.reactions.length()").value(2));
    mockMvc.perform(get("/chats/" + chat.getId() + "/messages?before=" + later.getId()).session(anaSession))
        .andExpect(jsonPath("$.reactions.length()").value(1))
        .andExpect(jsonPath("$.reactions[0].emoji").value(HEART));
    mockMvc.perform(get("/chats/" + chat.getId() + "/messages?before=" + message.getId()).session(anaSession))
        .andExpect(jsonPath("$.reactions.length()").value(0));
  }

  @Test
  @DisplayName("Deleting a message deletes its reactions (GR10)")
  void deletedWithTheMessage() throws Exception {
    // Arrange
    react(anaSession, message, HEART).andExpect(status().isOk());
    entityManager.flush();
    entityManager.clear();

    // Act
    mockMvc.perform(delete("/chats/" + chat.getId() + "/messages/" + message.getId()).with(csrf())
        .session(ruiSession))
        .andExpect(status().isNoContent());
    entityManager.flush();
    entityManager.clear();

    // Assert
    assertEquals(0, reactionRepository.findAll().stream()
        .filter(reaction -> reaction.getUser().getNumber().equals(ana.getNumber()))
        .count());
  }

  @Test
  @DisplayName("The last one leaving a group chat deletes it with its reactions")
  void deletedWithTheChat() throws Exception {
    // Arrange
    CustomChat group = chatRepository.save(new CustomChat("Study group", null, ana, List.of(rui, eva)));
    Message inGroup = messageRepository.saveAndFlush(new Message(group, ana, "hi"));
    react(anaSession, inGroup, HEART).andExpect(status().isOk());

    // Act
    for (MockHttpSession session : List.of(ruiSession, evaSession, anaSession)) {
      mockMvc.perform(post("/chats/" + group.getId() + "/leave").with(csrf()).session(session))
          .andExpect(status().isNoContent());
    }
    entityManager.flush();
    entityManager.clear();

    // Assert
    assertEquals(0, reactionRepository.findAll().stream()
        .filter(reaction -> reaction.getUser().getNumber().equals(ana.getNumber()))
        .count());
  }

  @ParameterizedTest
  @ValueSource(strings = { "👍", "👍🏽", "❤️", "©️", "🇵🇹", "1️⃣", "#️⃣", "👨‍👩‍👧", "👩🏽‍❤️‍💋‍👨🏽", "🏳️‍🌈", "🏴󠁧󠁢󠁳󠁣󠁴󠁿" })
  @DisplayName("Any single emoji: skin tones, flags, keycaps and families too")
  void singleEmoji(String emoji) throws Exception {
    // Act + Assert
    react(anaSession, message, emoji).andExpect(status().isOk());
    openChat(ruiSession, chat).andExpect(jsonPath("$.reactions[0].emoji").value(emoji));
  }

  @ParameterizedTest
  @ValueSource(strings = { "", " ", "a", "ok", "1", "#", "👍👍", "👍a", " 👍", "❤", "🇵", "🇵🇹🇵", "\\uFE0F", "👍\\uFE0F",
      "👍\\u200D" })
  @DisplayName("Anything that isn't one emoji is INVALID_EMOJI")
  void notAnEmoji(String text) throws Exception {
    // Act + Assert
    react(anaSession, message, text)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_EMOJI"));
  }

  @Test
  @DisplayName("Emoji joined past 64 characters are INVALID_EMOJI, 64 isn't the problem")
  void longestEmoji() throws Exception {
    // Arrange - each one is 2 characters and the joiner 1, so 21 are 62 and 22 are 65
    String joiner = "\\u200D";
    String fits = String.join(joiner, Collections.nCopies(21, "👩"));
    String tooLong = String.join(joiner, Collections.nCopies(22, "👩"));

    // Act + Assert
    react(anaSession, message, fits).andExpect(status().isOk());
    react(anaSession, message, tooLong)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_EMOJI"));
  }

  @Test
  @DisplayName("No emoji in the request is 400")
  void missingEmoji() throws Exception {
    // Act + Assert
    mockMvc.perform(put(url(message)).with(csrf()).session(anaSession)
        .contentType(MediaType.APPLICATION_JSON)
        .content("{}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("Someone who isn't in the chat gets NOT_FOUND, reacting or taking it off (GR8)")
  void notAMember() throws Exception {
    // Act + Assert
    react(evaSession, message, HEART)
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    removeReaction(evaSession, message).andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("A message of another chat, or one that's gone, is NOT_FOUND")
  void messageNotInTheChat() throws Exception {
    // Arrange
    CustomChat group = chatRepository.save(new CustomChat("Study group", null, ana, List.of(rui, eva)));
    Message elsewhere = messageRepository.saveAndFlush(new Message(group, ana, "hi all"));

    // Act + Assert
    mockMvc.perform(put("/chats/" + chat.getId() + "/messages/" + elsewhere.getId() + "/reaction").with(csrf())
        .session(anaSession).contentType(MediaType.APPLICATION_JSON).content("{\"emoji\":\"" + HEART + "\"}"))
        .andExpect(status().isNotFound());
    mockMvc.perform(delete("/chats/" + chat.getId() + "/messages/0/reaction").with(csrf()).session(anaSession))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("A blocked user's reaction shows for them and never for who blocked them, not even unblocked (GR9)")
  void blockedReactionHidden() throws Exception {
    // Arrange
    UserBlock block = userBlockRepository.saveAndFlush(new UserBlock(rui, ana));
    react(anaSession, message, HEART).andExpect(status().isOk());

    // Act + Assert
    openChat(anaSession, chat).andExpect(jsonPath("$.reactions.length()").value(1));
    userBlockRepository.deleteById(block.getId());
    openChat(ruiSession, chat).andExpect(jsonPath("$.reactions.length()").value(0));
    react(anaSession, message, HEART).andExpect(status().isOk());
    openChat(ruiSession, chat).andExpect(jsonPath("$.reactions.length()").value(0));
    react(anaSession, message, LAUGH).andExpect(status().isOk());
    openChat(ruiSession, chat).andExpect(jsonPath("$.reactions[0].emoji").value(LAUGH));
  }

  @Test
  @DisplayName("Replacing a reaction while blocked hides the new one, and the old one is gone for the blocker (GR9)")
  void replacedWhileBlocked() throws Exception {
    // Arrange
    react(anaSession, message, HEART).andExpect(status().isOk());
    UserBlock block = userBlockRepository.saveAndFlush(new UserBlock(rui, ana));

    // Act
    react(anaSession, message, LAUGH).andExpect(status().isOk());

    // Assert
    openChat(anaSession, chat).andExpect(jsonPath("$.reactions[0].emoji").value(LAUGH));
    userBlockRepository.deleteById(block.getId());
    openChat(ruiSession, chat).andExpect(jsonPath("$.reactions.length()").value(0));
  }

  @Test
  @DisplayName("The one who blocked doesn't have the DM until they unblock, so reacting is NOT_FOUND (GR9)")
  void blockerUnblocksFirst() throws Exception {
    // Arrange
    react(ruiSession, message, HEART).andExpect(status().isOk());
    userBlockRepository.saveAndFlush(new UserBlock(rui, ana));

    // Act + Assert
    react(ruiSession, message, LAUGH).andExpect(status().isNotFound());
    removeReaction(ruiSession, message).andExpect(status().isNotFound());
    openChat(anaSession, chat).andExpect(jsonPath("$.reactions[0].emoji").value(HEART));
  }

  @Test
  @DisplayName("A message hidden from you is NOT_FOUND, and its author's reaction to it stays hidden too (GR9)")
  void hiddenMessage() throws Exception {
    // Arrange - Rui had blocked Ana and unblocked since
    Message hidden = new Message(chat, ana, "see you at 10");
    hidden.hideFrom(rui);
    hidden = messageRepository.saveAndFlush(hidden);

    // Act + Assert
    react(ruiSession, hidden, HEART).andExpect(status().isNotFound());
    react(anaSession, hidden, HEART).andExpect(status().isOk());
    openChat(anaSession, chat).andExpect(jsonPath("$.reactions.length()").value(1));
    openChat(ruiSession, chat).andExpect(jsonPath("$.reactions.length()").value(0));
  }

  @Test
  @DisplayName("Blocks change nothing in a group chat (GR9)")
  void groupChatIgnoresBlocks() throws Exception {
    // Arrange
    CustomChat group = chatRepository.save(new CustomChat("Study group", null, ana, List.of(rui, eva)));
    Message inGroup = messageRepository.saveAndFlush(new Message(group, eva, "hi all"));
    userBlockRepository.saveAndFlush(new UserBlock(rui, ana));

    // Act
    react(anaSession, inGroup, HEART).andExpect(status().isOk());
    react(ruiSession, inGroup, LAUGH).andExpect(status().isOk());

    // Assert
    openChat(ruiSession, group).andExpect(jsonPath("$.reactions.length()").value(2));
  }

  @Test
  @DisplayName("Reacting isn't a message: the list keeps its last message and nothing is unread")
  void notUnread() throws Exception {
    // Arrange
    mockMvc.perform(put("/chats/" + chat.getId() + "/read").with(csrf()).session(ruiSession))
        .andExpect(status().isNoContent());

    // Act
    react(anaSession, message, HEART).andExpect(status().isOk());

    // Assert
    mockMvc.perform(get("/chats/").session(ruiSession))
        .andExpect(jsonPath("$[0].lastMessage.id").value(message.getId()))
        .andExpect(jsonPath("$[0].unread").value(0));
  }

  @Test
  @DisplayName("Staff can't react: 403, and without logging in it's 401")
  void onlyStudentsAndTeachers() throws Exception {
    // Arrange
    Staff staff = new Staff("it.react.staff@example.com", "it.react.staff@studymatch.com", "Staff React Test",
        passwordHash);
    staff.setMustChangePassword(false);
    staffRepository.save(staff);
    MockHttpSession staffSession = loginAs(staff.getInstitutionalEmail());

    // Act + Assert
    react(staffSession, message, HEART).andExpect(status().isForbidden());
    removeReaction(staffSession, message).andExpect(status().isForbidden());
    mockMvc.perform(put(url(message)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
        .content("{\"emoji\":\"" + HEART + "\"}"))
        .andExpect(status().isUnauthorized());
    mockMvc.perform(delete(url(message)).with(csrf())).andExpect(status().isUnauthorized());
  }

}
