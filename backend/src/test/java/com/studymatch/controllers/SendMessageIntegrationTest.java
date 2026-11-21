package com.studymatch.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;
import com.studymatch.models.Degree;
import com.studymatch.models.DirectChat;
import com.studymatch.models.Message;
import com.studymatch.models.PersonalityProfile;
import com.studymatch.models.Staff;
import com.studymatch.models.Student;
import com.studymatch.models.Teacher;
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
class SendMessageIntegrationTest {

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
  private Teacher teo;
  private Staff staff;
  private DirectChat anaAndRui;
  private MockHttpSession anaSession;

  // Ana already has a DM with Rui, not with Eva or Teo (rolled back after each test)
  @BeforeEach
  void setUp() throws Exception {
    degree = degreeRepository.save(new Degree("IT Message Degree", 3));
    ana = saveStudent("it.msg.ana@example.com", "Ana Message Test");
    rui = saveStudent("it.msg.rui@example.com", "Rui Message Test");
    eva = saveStudent("it.msg.eva@example.com", "Eva Message Test");
    teo = new Teacher("it.msg.teo@example.com", "it.msg.teo@studymatch.com", "Teo Message Test",
        passwordEncoder.encode(PASSWORD));
    teo.setMustChangePassword(false);
    teo = teacherRepository.save(teo);
    staff = new Staff("it.msg.staff@example.com", "it.msg.staff@studymatch.com", "Staff Message Test",
        passwordEncoder.encode(PASSWORD));
    staff.setMustChangePassword(false);
    staff = staffRepository.save(staff);
    anaAndRui = chatRepository.save(new DirectChat(ana, rui));
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

  private ResultActions send(String path, MockHttpSession session, String text) throws Exception {
    return mockMvc.perform(post(path).session(session).with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"text\":\"" + text + "\"}"));
  }

  private Message savedMessage(MvcResult result) throws Exception {
    Number id = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    return messageRepository.findById(id.longValue()).orElseThrow();
  }

  // send to a chat

  @Test
  @DisplayName("A member sends a message: 201 with the message, saved in the chat")
  void memberSends() throws Exception {
    // Act
    MvcResult result = send("/chats/" + anaAndRui.getId() + "/messages", anaSession, "  hi Rui ")
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.chatId").value(anaAndRui.getId()))
        .andExpect(jsonPath("$.author.number").value(ana.getNumber()))
        .andExpect(jsonPath("$.author.name").value("Ana Message Test"))
        .andExpect(jsonPath("$.text").value("hi Rui"))
        .andExpect(jsonPath("$.sentAt").isNotEmpty())
        .andExpect(jsonPath("$.editedAt").isEmpty())
        .andReturn();

    // Assert
    Message message = savedMessage(result);
    assertEquals(anaAndRui.getId(), message.getChat().getId());
    assertEquals("hi Rui", message.getText());
  }

  @Test
  @DisplayName("Someone who isn't in the chat gets 404 NOT_FOUND and nothing is saved (GR8)")
  void nonMemberCantSend() throws Exception {
    // Arrange
    MockHttpSession evaSession = loginAs(eva.getInstitutionalEmail());
    long messagesBefore = messageRepository.count();

    // Act
    send("/chats/" + anaAndRui.getId() + "/messages", evaSession, "hello")
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));

    // Assert
    assertEquals(messagesBefore, messageRepository.count());
  }

  @Test
  @DisplayName("A chat that doesn't exist is the same 404 NOT_FOUND")
  void missingChat() throws Exception {
    // Act + Assert
    send("/chats/-1/messages", anaSession, "hello")
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
  }

  @Test
  @DisplayName("A message of 2000 characters is sent (GR10)")
  void textAtTheLimit() throws Exception {
    // Act + Assert
    send("/chats/" + anaAndRui.getId() + "/messages", anaSession, "a".repeat(2000))
        .andExpect(status().isCreated());
  }

  @Test
  @DisplayName("A message of 2001 characters is refused with VALIDATION_FAILED (GR10)")
  void textOverTheLimit() throws Exception {
    // Act + Assert
    send("/chats/" + anaAndRui.getId() + "/messages", anaSession, "a".repeat(2001))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.fieldErrors[0].field").value("text"))
        .andExpect(jsonPath("$.fieldErrors[0].code").value("SIZE_MAX"))
        .andExpect(jsonPath("$.fieldErrors[0].params.max").value(2000));
  }

  @Test
  @DisplayName("An empty message is refused with VALIDATION_FAILED (GR10)")
  void blankText() throws Exception {
    // Act + Assert
    send("/chats/" + anaAndRui.getId() + "/messages", anaSession, "   ")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.fieldErrors[0].code").value("NOT_BLANK"));
  }

  @Test
  @DisplayName("Staff can't send messages (403 ACCESS_DENIED)")
  void staffCantSend() throws Exception {
    // Arrange
    MockHttpSession staffSession = loginAs(staff.getInstitutionalEmail());

    // Act + Assert
    send("/chats/" + anaAndRui.getId() + "/messages", staffSession, "hello")
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
  }

  // first message of a DM

  @Test
  @DisplayName("The first message to someone creates the DM, and the lookup finds it after")
  void firstMessageCreatesDirectChat() throws Exception {
    // Act
    MvcResult result = send("/chats/direct/" + eva.getNumber() + "/messages", anaSession, "hi Eva")
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.text").value("hi Eva"))
        .andReturn();

    // Assert
    DirectChat created = (DirectChat) savedMessage(result).getChat();
    assertTrue(created.hasMember(ana) && created.hasMember(eva));
    mockMvc.perform(get("/chats/direct/{number}", ana.getNumber()).session(loginAs(eva.getInstitutionalEmail())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.chatId").value(created.getId()));
  }

  @Test
  @DisplayName("A Teacher can be messaged first too")
  void firstMessageToTeacher() throws Exception {
    // Act + Assert
    send("/chats/direct/" + teo.getNumber() + "/messages", anaSession, "hello teacher")
        .andExpect(status().isCreated());
  }

  @Test
  @DisplayName("A \"first\" message when the DM already exists goes into it, no second DM")
  void firstMessageReusesDirectChat() throws Exception {
    // Arrange
    long chatsBefore = chatRepository.count();

    // Act
    send("/chats/direct/" + rui.getNumber() + "/messages", anaSession, "hi again")
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.chatId").value(anaAndRui.getId()));

    // Assert
    assertEquals(chatsBefore, chatRepository.count());
  }

  @Test
  @DisplayName("Yourself, Staff or a number nobody has is 404 NOT_FOUND, and no DM is created")
  void notSomeoneYouCanMessage() throws Exception {
    // Arrange
    long chatsBefore = chatRepository.count();

    // Act
    for (Long number : new Long[] { ana.getNumber(), staff.getNumber(), -1L }) {
      send("/chats/direct/" + number + "/messages", anaSession, "hello")
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    // Assert
    assertEquals(chatsBefore, chatRepository.count());
  }

  @Test
  @DisplayName("An empty first message doesn't create the DM")
  void blankFirstMessage() throws Exception {
    // Arrange
    long chatsBefore = chatRepository.count();

    // Act
    send("/chats/direct/" + eva.getNumber() + "/messages", anaSession, "")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

    // Assert
    assertEquals(chatsBefore, chatRepository.count());
  }

}
