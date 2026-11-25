package com.studymatch.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
import com.studymatch.models.Teacher;
import com.studymatch.models.User;
import com.studymatch.models.UserBlock;
import com.studymatch.repositories.ChatRepository;
import com.studymatch.repositories.DegreeRepository;
import com.studymatch.repositories.MessageRepository;
import com.studymatch.repositories.StaffRepository;
import com.studymatch.repositories.StudentRepository;
import com.studymatch.repositories.TeacherRepository;
import com.studymatch.repositories.UserBlockRepository;

import com.jayway.jsonpath.JsonPath;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SearchChatIntegrationTest {

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
  private UserBlockRepository userBlockRepository;

  @Autowired
  private PasswordEncoder passwordEncoder;

  private Student ana;
  private Student rui;
  private Student eva;
  private DirectChat chat;
  private MockHttpSession anaSession;
  private MockHttpSession ruiSession;

  // ana and rui share an empty DM, eva isn't in it (rolled back after each test)
  @BeforeEach
  void setUp() throws Exception {
    Degree degree = degreeRepository.save(new Degree("IT Search Chat Degree", 3));
    ana = saveStudent("it.search.ana@example.com", "Ana Search Test", degree);
    rui = saveStudent("it.search.rui@example.com", "Rui Search Test", degree);
    eva = saveStudent("it.search.eva@example.com", "Eva Search Test", degree);
    chat = chatRepository.save(new DirectChat(ana, rui));
    anaSession = loginAs(ana.getInstitutionalEmail());
    ruiSession = loginAs(rui.getInstitutionalEmail());
  }

  private Student saveStudent(String personalEmail, String name, Degree degree) {
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

  private Message message(Chat chat, User author, String text) {
    return messageRepository.saveAndFlush(new Message(chat, author, text));
  }

  // one ana sent while rui had her blocked, rui has unblocked her since
  private Message hiddenFromRui(String text) {
    Message hidden = new Message(chat, ana, text);
    hidden.hideFrom(rui);
    return messageRepository.saveAndFlush(hidden);
  }

  // "message 1", "message 2"... oldest first
  private List<Message> messages(int count) {
    List<Message> sent = new ArrayList<>();
    for (int i = 1; i <= count; i++) {
      sent.add(message(chat, i % 2 == 0 ? ana : rui, "message " + i));
    }
    return sent;
  }

  // "message from".."message to", up or down
  private static List<String> texts(int from, int to) {
    List<String> up = IntStream.rangeClosed(Math.min(from, to), Math.max(from, to))
        .mapToObj(i -> "message " + i)
        .toList();
    return from <= to ? up : up.reversed();
  }

  private static List<String> textsIn(ResultActions response) throws Exception {
    return JsonPath.read(response.andReturn().getResponse().getContentAsString(), "$.messages[*].text");
  }

  private ResultActions search(MockHttpSession session, Chat chat, String text) throws Exception {
    return mockMvc.perform(get("/chats/" + chat.getId() + "/messages/search").param("text", text).session(session));
  }

  private ResultActions around(MockHttpSession session, Chat chat, Long messageId) throws Exception {
    return mockMvc.perform(get("/chats/" + chat.getId() + "/messages/" + messageId + "/around").session(session));
  }

  private ResultActions page(MockHttpSession session, String cursor, Message message) throws Exception {
    return mockMvc.perform(get("/chats/" + chat.getId() + "/messages")
        .param(cursor, message.getId().toString()).session(session));
  }

  // searching

  @Test
  @DisplayName("The messages with that text come newest first, with their author and time")
  void newestFirst() throws Exception {
    // Arrange
    message(chat, ana, "the exam is on Monday");
    message(chat, rui, "lunch?");
    Message newest = message(chat, rui, "good luck with the exam");

    // Act + Assert
    search(anaSession, chat, "exam")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.messages.length()").value(2))
        .andExpect(jsonPath("$.messages[0].id").value(newest.getId()))
        .andExpect(jsonPath("$.messages[0].author.number").value(rui.getNumber()))
        .andExpect(jsonPath("$.messages[0].sentAt").exists())
        .andExpect(jsonPath("$.messages[1].text").value("the exam is on Monday"))
        .andExpect(jsonPath("$.hasMore").value(false));
  }

  @ParameterizedTest
  @DisplayName("Capitals don't matter, accented letters included, and neither do spaces around the search")
  @CsvSource(ignoreLeadingAndTrailingWhitespace = false, value = { "EXAME", "exame", "não HÁ", "NÃO", "  há exame " })
  void anyCapitals(String searched) throws Exception {
    // Arrange
    message(chat, rui, "Amanhã Não Há Exame");

    // Act + Assert
    search(anaSession, chat, searched).andExpect(jsonPath("$.messages.length()").value(1));
  }

  @Test
  @DisplayName("Accents do matter")
  void accentsMatter() throws Exception {
    // Arrange
    message(chat, rui, "você vem?");

    // Act + Assert
    search(anaSession, chat, "voce").andExpect(jsonPath("$.messages.length()").value(0));
  }

  @ParameterizedTest
  @DisplayName("%, _ and \\ are searched as themselves, not as wildcards")
  @CsvSource({ "0%, 100% done", "a_b, file a_b", "c:\\, in c:\\docs", "\\d, in c:\\docs", "%_, odd %_ text" })
  void wildcards(String searched, String match) throws Exception {
    // Arrange
    message(chat, rui, "100 done, file acb, in c:docs, odd text");
    message(chat, rui, match);

    // Act + Assert
    search(anaSession, chat, searched)
        .andExpect(jsonPath("$.messages.length()").value(1))
        .andExpect(jsonPath("$.messages[0].text").value(match));
  }

  @ParameterizedTest
  @DisplayName("Fewer than 2 characters isn't a search, spaces around them don't count")
  @ValueSource(strings = { "", " ", "   ", "a", " a " })
  void tooShort(String searched) throws Exception {
    // Arrange
    message(chat, rui, "a a a");

    // Act + Assert
    search(anaSession, chat, searched)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("SEARCH_TOO_SHORT"))
        .andExpect(jsonPath("$.params.min").value(2));
  }

  @Test
  @DisplayName("2 characters is enough, and so is an emoji")
  void twoCharacters() throws Exception {
    // Arrange
    message(chat, rui, "ok");
    message(chat, rui, "great 😂");

    // Act + Assert
    search(anaSession, chat, "ok").andExpect(jsonPath("$.messages.length()").value(1));
    search(anaSession, chat, "😂").andExpect(jsonPath("$.messages.length()").value(1));
  }

  @Test
  @DisplayName("Without the text at all it's VALIDATION_FAILED")
  void missingText() throws Exception {
    // Act + Assert
    mockMvc.perform(get("/chats/" + chat.getId() + "/messages/search").session(anaSession))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
  }

  @Test
  @DisplayName("Nothing matches: an empty list, not an error")
  void nothingMatches() throws Exception {
    // Arrange
    message(chat, rui, "lunch?");

    // Act + Assert
    search(anaSession, chat, "exam")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.messages.length()").value(0))
        .andExpect(jsonPath("$.hasMore").value(false));
  }

  @Test
  @DisplayName("Exactly 25 results: all of them and nothing more to load")
  void exactlyOnePage() throws Exception {
    // Arrange
    messages(25);

    // Act
    ResultActions found = search(anaSession, chat, "message");

    // Assert
    found.andExpect(jsonPath("$.hasMore").value(false));
    assertEquals(texts(25, 1), textsIn(found));
  }

  @Test
  @DisplayName("26 results: the newest 25, then the last one from the oldest shown")
  void nextPage() throws Exception {
    // Arrange
    List<Message> sent = messages(26);

    // Act
    ResultActions firstPage = search(anaSession, chat, "message");
    ResultActions nextPage = mockMvc.perform(get("/chats/" + chat.getId() + "/messages/search")
        .param("text", "message").param("before", sent.get(1).getId().toString()).session(anaSession));

    // Assert
    firstPage.andExpect(jsonPath("$.hasMore").value(true));
    assertEquals(texts(26, 2), textsIn(firstPage));
    nextPage.andExpect(jsonPath("$.hasMore").value(false));
    assertEquals(texts(1, 1), textsIn(nextPage));
  }

  @Test
  @DisplayName("Only this chat is searched")
  void onlyThisChat() throws Exception {
    // Arrange
    DirectChat withEva = chatRepository.save(new DirectChat(ana, eva));
    message(withEva, eva, "exam in the other chat");
    message(chat, rui, "exam in this chat");

    // Act + Assert
    assertEquals(List.of("exam in this chat"), textsIn(search(anaSession, chat, "exam")));
  }

  @Test
  @DisplayName("A reply is found by its own text and not by the one it quotes")
  void quoteIsntSearched() throws Exception {
    // Arrange
    Message original = message(chat, rui, "when is the exam?");
    messageRepository.saveAndFlush(new Message(chat, ana, "on Monday", original));

    // Act + Assert
    assertEquals(List.of("when is the exam?"), textsIn(search(anaSession, chat, "exam")));
    search(anaSession, chat, "monday")
        .andExpect(jsonPath("$.messages.length()").value(1))
        .andExpect(jsonPath("$.messages[0].replyTo.text").value("when is the exam?"));
  }

  @Test
  @DisplayName("An edited message is found by its new text only")
  void editedMessage() throws Exception {
    // Arrange
    Message edited = message(chat, rui, "exam on Monday");
    edited.edit("exam on Friday");
    messageRepository.saveAndFlush(edited);

    // Act + Assert
    search(anaSession, chat, "friday").andExpect(jsonPath("$.messages.length()").value(1));
    search(anaSession, chat, "monday").andExpect(jsonPath("$.messages.length()").value(0));
  }

  @Test
  @DisplayName("A message hidden from you isn't found, its author still finds it (GR9)")
  void hiddenMessage() throws Exception {
    // Arrange
    hiddenFromRui("hidden exam");

    // Act + Assert
    search(ruiSession, chat, "exam").andExpect(jsonPath("$.messages.length()").value(0));
    search(anaSession, chat, "exam").andExpect(jsonPath("$.messages.length()").value(1));
  }

  @Test
  @DisplayName("A result replying to a message hidden from you comes without the quote (GR9)")
  void hiddenQuote() throws Exception {
    // Arrange
    Message hidden = hiddenFromRui("hidden");
    messageRepository.saveAndFlush(new Message(chat, ana, "about the exam", hidden));

    // Act + Assert
    search(ruiSession, chat, "exam")
        .andExpect(jsonPath("$.messages[0].reply").value(true))
        .andExpect(jsonPath("$.messages[0].replyTo").doesNotExist());
  }

  @Test
  @DisplayName("In a group chat a blocked member's messages are found, the frontend keeps them behind the click (GR9)")
  void groupChatIgnoresBlocks() throws Exception {
    // Arrange
    CustomChat group = chatRepository.save(new CustomChat("Study group", null, ana, List.of(rui, eva)));
    message(group, rui, "exam notes");
    userBlockRepository.saveAndFlush(new UserBlock(ana, rui));

    // Act + Assert
    assertEquals(List.of("exam notes"), textsIn(search(anaSession, group, "exam")));
  }

  // jumping to a result

  @Test
  @DisplayName("A message in the middle comes with 25 on each side, oldest first")
  void aroundTheMiddle() throws Exception {
    // Arrange
    List<Message> sent = messages(53);

    // Act
    ResultActions found = around(anaSession, chat, sent.get(26).getId());

    // Assert - 27 is in the middle, 1 and 53 are left out
    found.andExpect(status().isOk())
        .andExpect(jsonPath("$.hasMore").value(true))
        .andExpect(jsonPath("$.hasNewer").value(true));
    assertEquals(texts(2, 52), textsIn(found));
  }

  @Test
  @DisplayName("Exactly 25 on each side: nothing more either way")
  void aroundExactly() throws Exception {
    // Arrange
    List<Message> sent = messages(51);

    // Act
    ResultActions found = around(anaSession, chat, sent.get(25).getId());

    // Assert
    found.andExpect(jsonPath("$.hasMore").value(false))
        .andExpect(jsonPath("$.hasNewer").value(false));
    assertEquals(texts(1, 51), textsIn(found));
  }

  @Test
  @DisplayName("The first message of the chat has nothing before it")
  void aroundTheFirst() throws Exception {
    // Arrange
    List<Message> sent = messages(27);

    // Act
    ResultActions found = around(anaSession, chat, sent.getFirst().getId());

    // Assert
    found.andExpect(jsonPath("$.hasMore").value(false))
        .andExpect(jsonPath("$.hasNewer").value(true));
    assertEquals(texts(1, 26), textsIn(found));
  }

  @Test
  @DisplayName("The latest message has nothing after it")
  void aroundTheLatest() throws Exception {
    // Arrange
    List<Message> sent = messages(27);

    // Act
    ResultActions found = around(anaSession, chat, sent.getLast().getId());

    // Assert
    found.andExpect(jsonPath("$.hasMore").value(true))
        .andExpect(jsonPath("$.hasNewer").value(false));
    assertEquals(texts(2, 27), textsIn(found));
  }

  @Test
  @DisplayName("The reactions come with it, like any page of the chat")
  void aroundWithReactions() throws Exception {
    // Arrange
    List<Message> sent = messages(3);
    mockMvc.perform(put("/chats/" + chat.getId() + "/messages/" + sent.getFirst().getId() + "/reaction").with(csrf())
        .session(ruiSession).contentType(MediaType.APPLICATION_JSON).content("{\"emoji\":\"😂\"}"))
        .andExpect(status().isOk());

    // Act + Assert
    around(anaSession, chat, sent.get(1).getId())
        .andExpect(jsonPath("$.reactions.length()").value(1))
        .andExpect(jsonPath("$.reactions[0].messageId").value(sent.getFirst().getId()));
  }

  @Test
  @DisplayName("Messages hidden from you aren't around it, and jumping to one is NOT_FOUND (GR9)")
  void aroundHidden() throws Exception {
    // Arrange
    Message before = message(chat, rui, "before");
    Message hidden = hiddenFromRui("hidden");
    message(chat, rui, "after");

    // Act + Assert
    assertEquals(List.of("before", "after"), textsIn(around(ruiSession, chat, before.getId())));
    around(ruiSession, chat, hidden.getId()).andExpect(status().isNotFound());
    assertEquals(List.of("before", "hidden", "after"), textsIn(around(anaSession, chat, hidden.getId())));
  }

  @Test
  @DisplayName("A message of another chat, or one that's gone, is NOT_FOUND")
  void aroundNotInTheChat() throws Exception {
    // Arrange
    DirectChat withEva = chatRepository.save(new DirectChat(ana, eva));
    Message elsewhere = message(withEva, eva, "other chat");
    message(chat, rui, "this chat");

    // Act + Assert
    around(anaSession, chat, elsewhere.getId())
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    around(anaSession, chat, 0L).andExpect(status().isNotFound());
  }

  // scrolling down from there

  @Test
  @DisplayName("51 after it: 50 oldest first, then the last one from the newest shown")
  void scrollingDown() throws Exception {
    // Arrange
    List<Message> sent = messages(52);

    // Act
    ResultActions firstPage = page(anaSession, "after", sent.getFirst());
    ResultActions nextPage = page(anaSession, "after", sent.get(50));

    // Assert
    firstPage.andExpect(jsonPath("$.hasMore").value(true))
        .andExpect(jsonPath("$.hasNewer").value(true));
    assertEquals(texts(2, 51), textsIn(firstPage));
    nextPage.andExpect(jsonPath("$.hasMore").value(true))
        .andExpect(jsonPath("$.hasNewer").value(false));
    assertEquals(texts(52, 52), textsIn(nextPage));
  }

  @Test
  @DisplayName("Exactly 50 after it: all of them, and that's the latest")
  void scrollingDownExactly() throws Exception {
    // Arrange
    List<Message> sent = messages(51);

    // Act
    ResultActions found = page(anaSession, "after", sent.getFirst());

    // Assert
    found.andExpect(jsonPath("$.hasNewer").value(false));
    assertEquals(texts(2, 51), textsIn(found));
  }

  @Test
  @DisplayName("Nothing after the latest message")
  void afterTheLatest() throws Exception {
    // Arrange
    List<Message> sent = messages(2);

    // Act + Assert
    page(anaSession, "after", sent.getLast())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.messages.length()").value(0))
        .andExpect(jsonPath("$.hasNewer").value(false));
  }

  @Test
  @DisplayName("Scrolling down skips messages hidden from you and other chats' ones (GR8, GR9)")
  void scrollingDownOnlyVisible() throws Exception {
    // Arrange
    Message first = message(chat, rui, "first");
    hiddenFromRui("hidden");
    message(chatRepository.save(new DirectChat(rui, eva)), eva, "other chat");
    message(chat, ana, "last");

    // Act + Assert
    assertEquals(List.of("last"), textsIn(page(ruiSession, "after", first)));
  }

  @Test
  @DisplayName("Opening the chat is the latest page, scrolling up isn't")
  void hasNewer() throws Exception {
    // Arrange
    List<Message> sent = messages(2);

    // Act + Assert
    mockMvc.perform(get("/chats/" + chat.getId() + "/messages").session(anaSession))
        .andExpect(jsonPath("$.hasNewer").value(false));
    page(anaSession, "before", sent.getLast())
        .andExpect(jsonPath("$.messages.length()").value(1))
        .andExpect(jsonPath("$.hasNewer").value(true));
  }

  @Test
  @DisplayName("before and after together is VALIDATION_FAILED")
  void beforeAndAfter() throws Exception {
    // Act + Assert
    mockMvc.perform(get("/chats/" + chat.getId() + "/messages").param("before", "5").param("after", "1")
        .session(anaSession))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
  }

  // who can

  @Test
  @DisplayName("Someone who isn't in the chat gets NOT_FOUND for all of it, as if it didn't exist (GR8)")
  void nonMemberNotFound() throws Exception {
    // Arrange
    Message sent = message(chat, rui, "private exam");
    MockHttpSession evaSession = loginAs(eva.getInstitutionalEmail());

    // Act + Assert - a search that's too short as well, the chat is checked first
    search(evaSession, chat, "exam").andExpect(status().isNotFound());
    search(evaSession, chat, "e").andExpect(status().isNotFound());
    around(evaSession, chat, sent.getId()).andExpect(status().isNotFound());
    page(evaSession, "after", sent).andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("The one who blocked doesn't have the DM until they unblock, the blocked one still does (GR9)")
  void blockerNotFound() throws Exception {
    // Arrange
    Message sent = message(chat, rui, "exam");
    userBlockRepository.saveAndFlush(new UserBlock(rui, ana));

    // Act + Assert
    search(ruiSession, chat, "exam").andExpect(status().isNotFound());
    around(ruiSession, chat, sent.getId()).andExpect(status().isNotFound());
    search(anaSession, chat, "exam").andExpect(jsonPath("$.messages.length()").value(1));
    around(anaSession, chat, sent.getId()).andExpect(status().isOk());
  }

  @Test
  @DisplayName("Teachers search their chats too")
  void teacherSearches() throws Exception {
    // Arrange
    Teacher teo = new Teacher("it.search.teo@example.com", "it.search.teo@studymatch.com", "Teo Search Test",
        passwordEncoder.encode(PASSWORD));
    teo.setMustChangePassword(false);
    teo = teacherRepository.save(teo);
    DirectChat withTeo = chatRepository.save(new DirectChat(teo, ana));
    Message sent = message(withTeo, ana, "about the exam");
    MockHttpSession teoSession = loginAs(teo.getInstitutionalEmail());

    // Act + Assert
    search(teoSession, withTeo, "exam").andExpect(jsonPath("$.messages.length()").value(1));
    around(teoSession, withTeo, sent.getId()).andExpect(status().isOk());
  }

  @Test
  @DisplayName("Staff don't have chats: 403")
  void staffForbidden() throws Exception {
    // Arrange
    Staff staff = new Staff("it.search.staff@example.com", "it.search.staff@studymatch.com", "Staff Search Test",
        passwordEncoder.encode(PASSWORD));
    staff.setMustChangePassword(false);
    staffRepository.save(staff);
    Message sent = message(chat, rui, "exam");
    MockHttpSession staffSession = loginAs(staff.getInstitutionalEmail());

    // Act + Assert
    search(staffSession, chat, "exam").andExpect(status().isForbidden());
    around(staffSession, chat, sent.getId()).andExpect(status().isForbidden());
  }

}
