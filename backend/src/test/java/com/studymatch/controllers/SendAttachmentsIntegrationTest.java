package com.studymatch.controllers;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;
import com.studymatch.models.AttachmentFile;
import com.studymatch.models.Chat;
import com.studymatch.models.CustomChat;
import com.studymatch.models.Degree;
import com.studymatch.models.DirectChat;
import com.studymatch.models.Message;
import com.studymatch.models.PersonalityProfile;
import com.studymatch.models.Staff;
import com.studymatch.models.Student;
import com.studymatch.models.Teacher;
import com.studymatch.models.UserBlock;
import com.studymatch.repositories.AttachmentFileRepository;
import com.studymatch.repositories.ChatRepository;
import com.studymatch.repositories.DegreeRepository;
import com.studymatch.repositories.MessageRepository;
import com.studymatch.repositories.StaffRepository;
import com.studymatch.repositories.StudentRepository;
import com.studymatch.repositories.TeacherRepository;
import com.studymatch.repositories.UserBlockRepository;

import jakarta.persistence.EntityManager;

@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SendAttachmentsIntegrationTest {

  private static final String PASSWORD = "Test1234";
  private static final int MAX_BYTES = 10 * 1024 * 1024;
  private static final byte[] PNG = HexFormat.of().parseHex("89504e470d0a1a0a00");

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
  private AttachmentFileRepository attachmentFileRepository;

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
  private MockHttpSession anaSession;
  private MockHttpSession ruiSession;

  // Ana has a DM with Rui, Eva isn't in it (rolled back after each test)
  @BeforeEach
  void setUp() throws Exception {
    passwordHash = passwordEncoder.encode(PASSWORD);
    degree = degreeRepository.save(new Degree("IT Attach Degree", 3));
    ana = saveStudent("it.attach.ana@example.com", "Ana Attach Test");
    rui = saveStudent("it.attach.rui@example.com", "Rui Attach Test");
    eva = saveStudent("it.attach.eva@example.com", "Eva Attach Test");
    chat = chatRepository.save(new DirectChat(ana, rui));
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

  // the browser's content type is wrong on purpose, it's never used
  private static MockMultipartFile file(String name, byte[] bytes) {
    return new MockMultipartFile("files", name, "text/html", bytes);
  }

  private static MockMultipartFile text(String name, String content) {
    return file(name, content.getBytes(StandardCharsets.UTF_8));
  }

  private static MockMultipartFile png(String name) {
    return file(name, PNG);
  }

  private static MockMultipartHttpServletRequestBuilder form(Chat chat, MockMultipartFile... files) {
    return formTo("/chats/" + chat.getId() + "/messages", files);
  }

  private static MockMultipartHttpServletRequestBuilder formTo(String url, MockMultipartFile... files) {
    MockMultipartHttpServletRequestBuilder form = multipart(url);
    for (MockMultipartFile file : files) {
      form.file(file);
    }
    return form;
  }

  // what's loaded here doesn't know what the database deleted
  private void forget() {
    entityManager.flush();
    entityManager.clear();
  }

  private ResultActions send(MockHttpSession session, Chat chat, MockMultipartFile... files) throws Exception {
    return mockMvc.perform(form(chat, files).session(session).with(csrf()));
  }

  // ana's message with those files, its JSON
  private String sent(Chat chat, MockMultipartFile... files) throws Exception {
    String message = send(anaSession, chat, files).andExpect(status().isCreated())
        .andReturn().getResponse().getContentAsString();
    forget();
    return message;
  }

  private static Long messageId(String message) {
    return JsonPath.<Number>read(message, "$.id").longValue();
  }

  private static Long fileId(String message, int index) {
    return JsonPath.<Number>read(message, "$.attachments[" + index + "].id").longValue();
  }

  // the first file of a message sent to the chat
  private ResultActions open(MockHttpSession session, Chat chat, String message) throws Exception {
    return open(session, chat, messageId(message), fileId(message, 0));
  }

  private ResultActions open(MockHttpSession session, Chat chat, Long messageId, Long fileId) throws Exception {
    return mockMvc.perform(get("/chats/" + chat.getId() + "/messages/" + messageId + "/attachments/" + fileId)
        .session(session));
  }

  private ResultActions openChat(MockHttpSession session, Chat chat) throws Exception {
    forget();
    return mockMvc.perform(get("/chats/" + chat.getId() + "/messages").session(session)).andExpect(status().isOk());
  }

  private ResultActions edit(String message, String text) throws Exception {
    return mockMvc.perform(put("/chats/" + chat.getId() + "/messages/" + messageId(message))
        .session(anaSession).with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"text\":\"" + text + "\"}"));
  }

  private long rows(String table) {
    entityManager.flush();
    return ((Number) entityManager.createNativeQuery("select count(*) from " + table).getSingleResult()).longValue();
  }

  // sending

  @Test
  @DisplayName("Files are sent with a text, in order, with the type of their extension and not the browser's")
  void sendFiles() throws Exception {
    // Act
    mockMvc.perform(form(chat, png("photo.png"), text("notes.txt", "olá")).param("text", "  here  ")
        .session(anaSession).with(csrf()))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.text").value("here"))
        .andExpect(jsonPath("$.author.number").value(ana.getNumber()))
        .andExpect(jsonPath("$.reply").value(false))
        .andExpect(jsonPath("$.attachments.length()").value(2))
        .andExpect(jsonPath("$.attachments[0].name").value("photo.png"))
        .andExpect(jsonPath("$.attachments[0].contentType").value("image/png"))
        .andExpect(jsonPath("$.attachments[0].size").value(PNG.length))
        .andExpect(jsonPath("$.attachments[1].name").value("notes.txt"))
        .andExpect(jsonPath("$.attachments[1].contentType").value("text/plain;charset=UTF-8"))
        .andExpect(jsonPath("$.attachments[1].size").value(4));

    // Assert - the other member gets the same, without what's in them
    openChat(ruiSession, chat)
        .andExpect(jsonPath("$.messages.length()").value(1))
        .andExpect(jsonPath("$.messages[0].text").value("here"))
        .andExpect(jsonPath("$.messages[0].attachments[0].name").value("photo.png"))
        .andExpect(jsonPath("$.messages[0].attachments[1].name").value("notes.txt"))
        .andExpect(jsonPath("$.messages[0].attachments[0].bytes").doesNotExist());
  }

  @Test
  @DisplayName("Without a text the message only has the files, and it's the chat's latest like any other")
  void filesOnly() throws Exception {
    // Act
    send(anaSession, chat, png("photo.png"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.text").value(""));

    // Assert
    forget();
    mockMvc.perform(get("/chats/").session(ruiSession))
        .andExpect(jsonPath("$[0].unread").value(1))
        .andExpect(jsonPath("$[0].lastMessage.text").value(""))
        .andExpect(jsonPath("$[0].lastMessage.attachments[0].name").value("photo.png"));
  }

  @Test
  @DisplayName("A text message has no attachments")
  void textOnly() throws Exception {
    // Act + Assert
    mockMvc.perform(post("/chats/" + chat.getId() + "/messages").session(anaSession).with(csrf())
        .contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"hello\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.attachments.length()").value(0));
  }

  @Test
  @DisplayName("The first DM can have files, it creates the chat")
  void firstDirectMessage() throws Exception {
    // Act
    mockMvc.perform(formTo("/chats/direct/" + eva.getNumber() + "/messages", png("photo.png"))
        .session(anaSession).with(csrf()))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.attachments[0].name").value("photo.png"));

    // Assert
    mockMvc.perform(get("/chats/direct/" + eva.getNumber()).session(anaSession))
        .andExpect(jsonPath("$.chatId").isNumber());
  }

  @Test
  @DisplayName("Someone you blocked needs unblocking before a DM with files too (GR9)")
  void blockedContact() throws Exception {
    // Arrange
    userBlockRepository.save(new UserBlock(ana, rui));

    // Act + Assert
    mockMvc.perform(formTo("/chats/direct/" + rui.getNumber() + "/messages", png("photo.png"))
        .session(anaSession).with(csrf()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("UNBLOCK_TO_MESSAGE"));
    assertEquals(0, messageRepository.count());
  }

  @Test
  @DisplayName("Files can reply, and a reply's quote shows the original's files")
  void replyWithFiles() throws Exception {
    // Arrange
    String original = sent(chat, png("photo.png"));

    // Act
    mockMvc.perform(form(chat, text("answer.txt", "42")).param("replyToId", messageId(original).toString())
        .session(ruiSession).with(csrf()))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.reply").value(true))
        .andExpect(jsonPath("$.replyTo.text").value(""))
        .andExpect(jsonPath("$.replyTo.attachments[0].name").value("photo.png"));

    // Assert
    openChat(anaSession, chat)
        .andExpect(jsonPath("$.messages[1].attachments[0].name").value("answer.txt"))
        .andExpect(jsonPath("$.messages[1].replyTo.attachments[0].name").value("photo.png"));
  }

  @Test
  @DisplayName("A reply with files to a message that isn't there is REPLIED_MESSAGE_NOT_FOUND")
  void replyToMissing() throws Exception {
    // Act + Assert
    mockMvc.perform(form(chat, png("photo.png")).param("replyToId", "0").session(anaSession).with(csrf()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("REPLIED_MESSAGE_NOT_FOUND"));
  }

  @Test
  @DisplayName("Teachers send files in group chats too")
  void teacherInGroup() throws Exception {
    // Arrange
    Teacher teo = new Teacher("it.attach.teo@example.com", "it.attach.teo@studymatch.com", "Teo Attach Test",
        passwordHash);
    teo.setMustChangePassword(false);
    teo = teacherRepository.save(teo);
    CustomChat group = chatRepository.save(new CustomChat("Study group", null, ana, List.of(rui, teo)));

    // Act
    String message = send(loginAs(teo.getInstitutionalEmail()), group, text("plan.txt", "chapter 1"))
        .andExpect(status().isCreated())
        .andReturn().getResponse().getContentAsString();

    // Assert
    forget();
    open(ruiSession, group, message).andExpect(status().isOk());
  }

  // the form

  @Test
  @DisplayName("A form without files is refused with VALIDATION_FAILED")
  void noFiles() throws Exception {
    // Act + Assert
    mockMvc.perform(form(chat).param("text", "hello").session(anaSession).with(csrf()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.fieldErrors[0].field").value("files"));
  }

  @Test
  @DisplayName("A text with files has the same limit: 2000 characters, 2001 is refused")
  void textLimit() throws Exception {
    // Act + Assert
    mockMvc.perform(form(chat, png("a.png")).param("text", "a".repeat(2000)).session(anaSession).with(csrf()))
        .andExpect(status().isCreated());
    mockMvc.perform(form(chat, png("a.png")).param("text", "a".repeat(2001)).session(anaSession).with(csrf()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors[0].code").value("SIZE_MAX"));
  }

  @Test
  @DisplayName("A replyToId that isn't a number is refused with VALIDATION_FAILED")
  void wrongReplyToId() throws Exception {
    // Act + Assert
    mockMvc.perform(form(chat, png("a.png")).param("replyToId", "abc").session(anaSession).with(csrf()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.fieldErrors[0].field").value("replyToId"));
  }

  // the files (GR10)

  @Test
  @DisplayName("5 files are accepted, 6 are refused and no message is sent")
  void fileCount() throws Exception {
    // Arrange
    MockMultipartFile[] five = { png("1.png"), png("2.png"), png("3.png"), png("4.png"), png("5.png") };
    MockMultipartFile[] six = Arrays.copyOf(five, 6);
    six[5] = png("6.png");

    // Act + Assert
    send(anaSession, chat, five)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.attachments.length()").value(5))
        .andExpect(jsonPath("$.attachments[4].name").value("5.png"));
    send(anaSession, chat, six)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("TOO_MANY_ATTACHMENTS"))
        .andExpect(jsonPath("$.params.max").value(5));
    assertEquals(5, attachmentFileRepository.count());
  }

  @Test
  @DisplayName("10 MB is accepted, one byte more is refused and names the file")
  void fileSize() throws Exception {
    // Act + Assert
    send(anaSession, chat, file("big.png", Arrays.copyOf(PNG, MAX_BYTES)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.attachments[0].size").value(MAX_BYTES));
    send(anaSession, chat, png("small.png"), file("big.png", Arrays.copyOf(PNG, MAX_BYTES + 1)))
        .andExpect(status().is(413))
        .andExpect(jsonPath("$.code").value("ATTACHMENT_TOO_LARGE"))
        .andExpect(jsonPath("$.params.name").value("big.png"))
        .andExpect(jsonPath("$.params.index").value(1))
        .andExpect(jsonPath("$.params.max").value(10));
  }

  @ParameterizedTest
  @ValueSource(strings = { "page.html", "drawing.svg", "run.exe", "old.doc", "noextension", "" })
  @DisplayName("A type that isn't allowed is refused and names the file")
  void typeNotAllowed(String name) throws Exception {
    // Act + Assert
    send(anaSession, chat, png("a.png"), file(name, PNG))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("ATTACHMENT_TYPE_NOT_ALLOWED"))
        .andExpect(jsonPath("$.params.name").value(name))
        .andExpect(jsonPath("$.params.index").value(1));
  }

  @ParameterizedTest
  @ValueSource(strings = { "page.png", "page.pdf", "page.docx" })
  @DisplayName("A file that isn't what its name says is refused and names it")
  void contentMismatch(String name) throws Exception {
    // Act + Assert
    send(anaSession, chat, text(name, "<script>alert(1)</script>"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("ATTACHMENT_CONTENT_MISMATCH"))
        .andExpect(jsonPath("$.params.name").value(name))
        .andExpect(jsonPath("$.params.index").value(0));
  }

  @Test
  @DisplayName("An empty file is refused")
  void emptyFile() throws Exception {
    // Act + Assert
    send(anaSession, chat, text("empty.txt", ""))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("ATTACHMENT_CONTENT_MISMATCH"));
  }

  @Test
  @DisplayName("The folders some browsers send go, a long name is cut and keeps its extension")
  void names() throws Exception {
    // Act + Assert
    send(anaSession, chat, png("C:\\Users\\ana\\photo.png"), png("a".repeat(300) + ".png"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.attachments[0].name").value("photo.png"))
        .andExpect(jsonPath("$.attachments[1].name").value("a".repeat(251) + ".png"));
  }

  // who can send

  @Test
  @DisplayName("Someone outside the chat gets 404 and nothing is sent (GR8)")
  void notAMember() throws Exception {
    // Act + Assert
    send(loginAs(eva.getInstitutionalEmail()), chat, png("photo.png"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    assertEquals(0, messageRepository.count());
  }

  @Test
  @DisplayName("Staff don't have chats, sending or opening: 403")
  void staffForbidden() throws Exception {
    // Arrange
    String message = sent(chat, png("photo.png"));
    Staff staff = new Staff("it.attach.staff@example.com", "it.attach.staff@studymatch.com", "Staff Attach Test",
        passwordHash);
    staff.setMustChangePassword(false);
    staffRepository.save(staff);
    MockHttpSession session = loginAs(staff.getInstitutionalEmail());

    // Act + Assert
    send(session, chat, png("photo.png")).andExpect(status().isForbidden());
    open(session, chat, message).andExpect(status().isForbidden());
  }

  // opening

  @Test
  @DisplayName("An image opens in the page with its real type, nosniff and a private cache")
  void openImage() throws Exception {
    // Arrange
    String message = sent(chat, png("photo.png"));

    // Act
    MockHttpServletResponse response = open(ruiSession, chat, message)
        .andExpect(status().isOk())
        .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "image/png"))
        .andExpect(header().string("X-Content-Type-Options", "nosniff"))
        .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "max-age=300, private"))
        .andReturn().getResponse();

    // Assert
    assertArrayEquals(PNG, response.getContentAsByteArray());
    ContentDisposition disposition = ContentDisposition.parse(response.getHeader(HttpHeaders.CONTENT_DISPOSITION));
    assertTrue(disposition.isInline());
    assertEquals("photo.png", disposition.getFilename());
  }

  @Test
  @DisplayName("Any other file downloads under its name, accents included")
  void downloadFile() throws Exception {
    // Arrange
    String message = sent(chat, text("resumo de cálculo.txt", "olá"));

    // Act
    MockHttpServletResponse response = open(ruiSession, chat, message)
        .andExpect(status().isOk())
        .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "text/plain;charset=UTF-8"))
        .andExpect(header().string("X-Content-Type-Options", "nosniff"))
        .andReturn().getResponse();

    // Assert
    assertArrayEquals("olá".getBytes(StandardCharsets.UTF_8), response.getContentAsByteArray());
    ContentDisposition disposition = ContentDisposition.parse(response.getHeader(HttpHeaders.CONTENT_DISPOSITION));
    assertTrue(disposition.isAttachment());
    assertEquals("resumo de cálculo.txt", disposition.getFilename());
  }

  @Test
  @DisplayName("Each file of a message opens as itself")
  void openSecond() throws Exception {
    // Arrange
    String message = sent(chat, text("1.txt", "one"), text("2.txt", "two"));

    // Act
    MockHttpServletResponse response = open(ruiSession, chat, messageId(message), fileId(message, 1))
        .andExpect(status().isOk())
        .andReturn().getResponse();

    // Assert
    assertEquals("two", response.getContentAsString());
  }

  @Test
  @DisplayName("Someone outside the chat can't open a file's link: 404 (GR8)")
  void outsiderOpens() throws Exception {
    // Arrange
    String message = sent(chat, png("photo.png"));

    // Act + Assert
    open(loginAs(eva.getInstitutionalEmail()), chat, message)
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
  }

  @Test
  @DisplayName("Someone removed from a group chat can't open its files anymore (GR8)")
  void removedMemberOpens() throws Exception {
    // Arrange
    CustomChat group = chatRepository.save(new CustomChat("Study group", null, ana, List.of(rui, eva)));
    String message = sent(group, png("photo.png"));
    open(ruiSession, group, message).andExpect(status().isOk());
    mockMvc.perform(delete("/chats/" + group.getId() + "/members/" + rui.getNumber())
        .session(anaSession).with(csrf())).andExpect(status().isOk());

    // Act + Assert
    open(ruiSession, group, message).andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("A file through another chat of yours, or another message, is 404")
  void throughAnother() throws Exception {
    // Arrange
    DirectChat withEva = chatRepository.save(new DirectChat(ana, eva));
    String message = sent(chat, png("photo.png"));
    String other = sent(withEva, png("other.png"));

    // Act + Assert
    open(anaSession, withEva, message).andExpect(status().isNotFound());
    open(anaSession, withEva, messageId(other), fileId(message, 0)).andExpect(status().isNotFound());
    open(anaSession, chat, messageId(message), fileId(other, 0)).andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("Files sent to someone who blocked you never reach them, not even after the unblock (GR9)")
  void hiddenFromBlocker() throws Exception {
    // Arrange
    UserBlock block = userBlockRepository.saveAndFlush(new UserBlock(rui, ana));
    String message = sent(chat, png("photo.png"));
    userBlockRepository.deleteById(block.getId());

    // Act + Assert
    open(anaSession, chat, message).andExpect(status().isOk());
    open(ruiSession, chat, message).andExpect(status().isNotFound());
    openChat(ruiSession, chat).andExpect(jsonPath("$.messages.length()").value(0));
  }

  // editing

  @Test
  @DisplayName("The text of a message with files can be added, changed and taken off, the files stay")
  void editText() throws Exception {
    // Arrange
    String message = sent(chat, png("photo.png"));

    // Act + Assert
    edit(message, "look").andExpect(status().isOk())
        .andExpect(jsonPath("$.text").value("look"))
        .andExpect(jsonPath("$.attachments[0].name").value("photo.png"));
    forget();
    edit(message, "  ").andExpect(status().isOk())
        .andExpect(jsonPath("$.text").value(""))
        .andExpect(jsonPath("$.editedAt").isNotEmpty());
    openChat(ruiSession, chat)
        .andExpect(jsonPath("$.messages[0].text").value(""))
        .andExpect(jsonPath("$.messages[0].attachments.length()").value(1));
  }

  @Test
  @DisplayName("Leaving a message without text as it was doesn't make it edited")
  void sameEmptyText() throws Exception {
    // Arrange
    String message = sent(chat, png("photo.png"));

    // Act + Assert
    edit(message, "").andExpect(status().isOk())
        .andExpect(jsonPath("$.editedAt").isEmpty());
  }

  // deleting

  @Test
  @DisplayName("Deleting a message deletes its files and what's in them, the other messages' stay (GR10)")
  void deleteMessage() throws Exception {
    // Arrange
    String message = sent(chat, png("1.png"), png("2.png"));
    String other = sent(chat, png("other.png"));

    // Act
    mockMvc.perform(delete("/chats/" + chat.getId() + "/messages/" + messageId(message))
        .session(anaSession).with(csrf())).andExpect(status().isNoContent());

    // Assert
    assertEquals(1, rows("attachment"));
    assertEquals(1, rows("attachment_file"));
    forget();
    open(ruiSession, chat, message).andExpect(status().isNotFound());
    open(ruiSession, chat, other).andExpect(status().isOk());
  }

  @Test
  @DisplayName("The last one out of a group chat takes its files with it")
  void deleteChat() throws Exception {
    // Arrange
    CustomChat group = chatRepository.save(new CustomChat("Study group", null, ana, List.of(rui, eva)));
    sent(group, png("photo.png"));

    // Act
    for (MockHttpSession session : List.of(ruiSession, loginAs(eva.getInstitutionalEmail()), anaSession)) {
      mockMvc.perform(post("/chats/" + group.getId() + "/leave").session(session).with(csrf()))
          .andExpect(status().isNoContent());
      forget();
    }

    // Assert
    assertEquals(0, rows("attachment"));
    assertEquals(0, rows("attachment_file"));
  }

  // what's loaded

  // opening the chat, its list and searching
  private Statistics statisticsOfReading() throws Exception {
    forget();
    Statistics statistics = entityManager.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
    statistics.clear();
    mockMvc.perform(get("/chats/" + chat.getId() + "/messages").session(ruiSession)).andExpect(status().isOk());
    mockMvc.perform(get("/chats/").session(ruiSession)).andExpect(status().isOk());
    mockMvc.perform(get("/chats/" + chat.getId() + "/messages/search").param("text", "file").session(ruiSession))
        .andExpect(status().isOk());
    return statistics;
  }

  @Test
  @DisplayName("Reading messages never loads what's in the files, and more of them don't mean more queries")
  void listsLoadNoFiles() throws Exception {
    // Arrange
    for (int i = 0; i < 2; i++) {
      mockMvc.perform(form(chat, png("photo.png")).param("text", "file").session(anaSession).with(csrf()));
    }
    long before = statisticsOfReading().getPrepareStatementCount();
    for (int i = 0; i < 10; i++) {
      mockMvc.perform(form(chat, png("1.png"), png("2.png")).param("text", "file").session(anaSession).with(csrf()));
    }

    // Act
    Statistics statistics = statisticsOfReading();

    // Assert
    assertEquals(before, statistics.getPrepareStatementCount());
    assertEquals(0, statistics.getEntityStatistics(AttachmentFile.class.getName()).getLoadCount());
  }

}
