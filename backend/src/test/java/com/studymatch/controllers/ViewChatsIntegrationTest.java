package com.studymatch.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
import org.springframework.transaction.annotation.Transactional;

import com.studymatch.models.ChatSettings;
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

import jakarta.persistence.EntityManager;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ViewChatsIntegrationTest {

  private static final String PASSWORD = "Test1234";

  // the 8-byte PNG signature, enough for the photo check
  private static final String PNG = "data:image/png;base64,iVBORw0KGgo=";

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
  private EntityManager entityManager;

  @Autowired
  private PasswordEncoder passwordEncoder;

  private Degree degree;
  private Student ana;
  private Student rui;
  private Student eva;
  private Teacher teo;
  private MockHttpSession anaSession;

  // nobody has a chat yet (rolled back after each test)
  @BeforeEach
  void setUp() throws Exception {
    degree = degreeRepository.save(new Degree("IT View Chats Degree", 3));
    ana = saveStudent("it.chats.ana@example.com", "Ana Chats Test");
    rui = saveStudent("it.chats.rui@example.com", "Rui Chats Test");
    eva = saveStudent("it.chats.eva@example.com", "Eva Chats Test");
    teo = new Teacher("it.chats.teo@example.com", "it.chats.teo@studymatch.com", "Teo Chats Test",
        passwordEncoder.encode(PASSWORD));
    teo.setMustChangePassword(false);
    teo = teacherRepository.save(teo);
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

  // the user opened the chat now, the sleep keeps the next messages strictly after lastReadAt
  private void markRead(DirectChat chat, User user) throws InterruptedException {
    ChatSettings settings = new ChatSettings(chat, user);
    settings.markRead();
    entityManager.persist(settings);
    Thread.sleep(5);
  }

  // the list

  @Test
  @DisplayName("No chats yet: an empty list and nothing unread")
  void noChats() throws Exception {
    // Act + Assert
    mockMvc.perform(get("/chats/").session(anaSession))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
    mockMvc.perform(get("/chats/unread-count").session(anaSession))
        .andExpect(status().isOk())
        .andExpect(content().string("0"));
  }

  @Test
  @DisplayName("The chat with the latest message comes first, even if it was started later")
  void latestMessageFirst() throws Exception {
    // Arrange - the DM with Rui is older but got the last message
    DirectChat withRui = chatRepository.save(new DirectChat(ana, rui));
    DirectChat withEva = chatRepository.save(new DirectChat(ana, eva));
    message(withRui, ana, "hi Rui");
    message(withEva, eva, "hi Ana");
    message(withRui, rui, "hey");

    // Act + Assert
    mockMvc.perform(get("/chats/").session(anaSession))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[0].chatId").value(withRui.getId()))
        .andExpect(jsonPath("$[1].chatId").value(withEva.getId()));
  }

  @Test
  @DisplayName("A DM shows the other person's name and number, and its last message with the author")
  void rowShowsTheOtherPerson() throws Exception {
    // Arrange
    DirectChat withRui = chatRepository.save(new DirectChat(ana, rui));
    message(withRui, ana, "first");
    Message last = message(withRui, rui, "second");

    // Act + Assert
    mockMvc.perform(get("/chats/").session(anaSession))
        .andExpect(jsonPath("$[0].name").value("Rui Chats Test"))
        .andExpect(jsonPath("$[0].contact.number").value(rui.getNumber()))
        .andExpect(jsonPath("$[0].contact.role").value("STUDENT"))
        .andExpect(jsonPath("$[0].lastMessage.id").value(last.getId()))
        .andExpect(jsonPath("$[0].lastMessage.text").value("second"))
        .andExpect(jsonPath("$[0].lastMessage.author.number").value(rui.getNumber()));
  }

  @Test
  @DisplayName("The other side sees the same DM under the first person's name")
  void otherSideSeesTheirName() throws Exception {
    // Arrange
    DirectChat withRui = chatRepository.save(new DirectChat(ana, rui));
    message(withRui, ana, "hi");

    // Act + Assert
    mockMvc.perform(get("/chats/").session(loginAs(rui.getInstitutionalEmail())))
        .andExpect(jsonPath("$[0].chatId").value(withRui.getId()))
        .andExpect(jsonPath("$[0].name").value("Ana Chats Test"));
  }

  @Test
  @DisplayName("Other people's DMs aren't in the list")
  void onlyYourChats() throws Exception {
    // Arrange
    DirectChat ruiAndEva = chatRepository.save(new DirectChat(rui, eva));
    message(ruiAndEva, rui, "not for Ana");

    // Act + Assert
    mockMvc.perform(get("/chats/").session(anaSession))
        .andExpect(jsonPath("$.length()").value(0));
  }

  @Test
  @DisplayName("Teachers have a chat list too")
  void teacherList() throws Exception {
    // Arrange
    DirectChat withAna = chatRepository.save(new DirectChat(teo, ana));
    message(withAna, ana, "hello teacher");

    // Act + Assert
    mockMvc.perform(get("/chats/").session(loginAs(teo.getInstitutionalEmail())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].name").value("Ana Chats Test"))
        .andExpect(jsonPath("$[0].unread").value(1));
  }

  // unread

  @Test
  @DisplayName("A chat never opened: every message from the other person is unread, your own never are")
  void neverOpenedCountsTheirMessages() throws Exception {
    // Arrange
    DirectChat withRui = chatRepository.save(new DirectChat(ana, rui));
    message(withRui, rui, "one");
    message(withRui, ana, "mine");
    message(withRui, rui, "two");

    // Act + Assert
    mockMvc.perform(get("/chats/").session(anaSession))
        .andExpect(jsonPath("$[0].unread").value(2));
  }

  @Test
  @DisplayName("Once opened, only the messages after lastReadAt are unread")
  void onlyMessagesAfterLastRead() throws Exception {
    // Arrange
    DirectChat withRui = chatRepository.save(new DirectChat(ana, rui));
    message(withRui, rui, "read");
    markRead(withRui, ana);
    message(withRui, rui, "new");

    // Act + Assert
    mockMvc.perform(get("/chats/").session(anaSession))
        .andExpect(jsonPath("$[0].unread").value(1));
  }

  @Test
  @DisplayName("Everything read: 0 unread, and the other person still has theirs")
  void allRead() throws Exception {
    // Arrange - Ana opened it after Rui's message, Rui never opened it
    DirectChat withRui = chatRepository.save(new DirectChat(ana, rui));
    message(withRui, ana, "hi");
    message(withRui, rui, "hey");
    markRead(withRui, ana);

    // Act + Assert
    mockMvc.perform(get("/chats/").session(anaSession))
        .andExpect(jsonPath("$[0].unread").value(0));
    mockMvc.perform(get("/chats/").session(loginAs(rui.getInstitutionalEmail())))
        .andExpect(jsonPath("$[0].unread").value(1));
  }

  @Test
  @DisplayName("The side menu's total adds up the unread messages of every chat")
  void totalAcrossChats() throws Exception {
    // Arrange - 2 unread with Rui, 1 with Eva (one before she was read doesn't count)
    DirectChat withRui = chatRepository.save(new DirectChat(ana, rui));
    DirectChat withEva = chatRepository.save(new DirectChat(ana, eva));
    message(withRui, rui, "one");
    message(withRui, rui, "two");
    message(withEva, eva, "old");
    markRead(withEva, ana);
    message(withEva, eva, "new");
    message(withEva, ana, "mine");

    // Act + Assert
    mockMvc.perform(get("/chats/unread-count").session(anaSession))
        .andExpect(status().isOk())
        .andExpect(content().string("3"));
  }

  @Test
  @DisplayName("Staff don't have chats: 403")
  void staffForbidden() throws Exception {
    // Arrange
    Staff staff = new Staff("it.chats.staff@example.com", "it.chats.staff@studymatch.com", "Staff Chats Test",
        passwordEncoder.encode(PASSWORD));
    staff.setMustChangePassword(false);
    staffRepository.save(staff);
    MockHttpSession staffSession = loginAs(staff.getInstitutionalEmail());

    // Act + Assert
    mockMvc.perform(get("/chats/").session(staffSession))
        .andExpect(status().isForbidden());
    mockMvc.perform(get("/chats/unread-count").session(staffSession))
        .andExpect(status().isForbidden());
  }

  // the picture

  @Test
  @DisplayName("A student's photo comes back as the image itself, cached privately")
  void pfpIsTheImage() throws Exception {
    // Arrange
    rui.setPfp(PNG);
    studentRepository.save(rui);

    // Act + Assert
    MvcResult result = mockMvc.perform(get("/students/" + rui.getNumber() + "/pfp").session(anaSession))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.IMAGE_PNG))
        .andExpect(header().string("Cache-Control", "max-age=300, private"))
        .andReturn();
    assertEquals(8, result.getResponse().getContentAsByteArray().length);
  }

  @Test
  @DisplayName("No photo, or no such student: NOT_FOUND, the frontend shows initials")
  void noPfp() throws Exception {
    // Act + Assert
    mockMvc.perform(get("/students/" + rui.getNumber() + "/pfp").session(anaSession))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    mockMvc.perform(get("/students/" + teo.getNumber() + "/pfp").session(anaSession))
        .andExpect(status().isNotFound());
  }

}
