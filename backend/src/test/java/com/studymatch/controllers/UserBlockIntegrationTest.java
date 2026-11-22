package com.studymatch.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
class UserBlockIntegrationTest {

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
  private DirectChat withRui;
  private Message beforeBlock;
  private MockHttpSession anaSession;
  private MockHttpSession ruiSession;

  // ana and rui share a DM with one message from rui that ana hasn't read, eva isn't in it (rolled back after each test)
  @BeforeEach
  void setUp() throws Exception {
    degree = degreeRepository.save(new Degree("IT User Block Degree", 3));
    ana = saveStudent("it.block.ana@example.com", "Ana Block Test");
    rui = saveStudent("it.block.rui@example.com", "Rui Block Test");
    eva = saveStudent("it.block.eva@example.com", "Eva Block Test");
    withRui = chatRepository.save(new DirectChat(ana, rui));
    beforeBlock = messageRepository.save(new Message(withRui, rui, "see you at 10"));
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

  private ResultActions block(Long number, MockHttpSession session) throws Exception {
    return mockMvc.perform(put("/blocks/" + number).session(session).with(csrf()));
  }

  private ResultActions unblock(Long number, MockHttpSession session) throws Exception {
    return mockMvc.perform(delete("/blocks/" + number).session(session).with(csrf()));
  }

  private ResultActions sendTo(Long chatId, String text, MockHttpSession session) throws Exception {
    return mockMvc.perform(post("/chats/" + chatId + "/messages").session(session).with(csrf())
        .contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"" + text + "\"}"));
  }

  private ResultActions sendDirect(Long number, String text, MockHttpSession session) throws Exception {
    return mockMvc.perform(post("/chats/direct/" + number + "/messages").session(session).with(csrf())
        .contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"" + text + "\"}"));
  }

  // blocking

  @Test
  @DisplayName("Blocking lists the person in your blocked users, by name")
  void blockAndList() throws Exception {
    // Act
    block(rui.getNumber(), anaSession).andExpect(status().isNoContent());
    block(eva.getNumber(), anaSession).andExpect(status().isNoContent());

    // Assert
    mockMvc.perform(get("/blocks/").session(anaSession))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[0].number").value(eva.getNumber()))
        .andExpect(jsonPath("$[0].name").value("Eva Block Test"))
        .andExpect(jsonPath("$[1].number").value(rui.getNumber()));
  }

  @Test
  @DisplayName("Blocking someone already blocked is still 204 and keeps one entry")
  void blockTwice() throws Exception {
    // Arrange
    block(rui.getNumber(), anaSession).andExpect(status().isNoContent());

    // Act
    block(rui.getNumber(), anaSession).andExpect(status().isNoContent());

    // Assert
    mockMvc.perform(get("/blocks/").session(anaSession))
        .andExpect(jsonPath("$.length()").value(1));
  }

  @Test
  @DisplayName("Unblocking takes them off the list")
  void unblockEmptiesTheList() throws Exception {
    // Arrange
    block(rui.getNumber(), anaSession).andExpect(status().isNoContent());

    // Act
    unblock(rui.getNumber(), anaSession).andExpect(status().isNoContent());

    // Assert
    mockMvc.perform(get("/blocks/").session(anaSession))
        .andExpect(jsonPath("$.length()").value(0));
  }

  @Test
  @DisplayName("Unblocking someone who isn't blocked is still 204")
  void unblockNotBlocked() throws Exception {
    // Act + Assert
    unblock(rui.getNumber(), anaSession).andExpect(status().isNoContent());
  }

  @Test
  @DisplayName("Blocking only works one way: the person you blocked hasn't blocked you")
  void blockIsOneWay() throws Exception {
    // Arrange
    block(rui.getNumber(), anaSession).andExpect(status().isNoContent());

    // Act + Assert
    mockMvc.perform(get("/blocks/").session(ruiSession))
        .andExpect(jsonPath("$.length()").value(0));
  }

  @Test
  @DisplayName("You can't block yourself: 400 CANNOT_BLOCK_YOURSELF")
  void blockYourself() throws Exception {
    // Act + Assert
    block(ana.getNumber(), anaSession)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("CANNOT_BLOCK_YOURSELF"));
  }

  @Test
  @DisplayName("Teachers block and can be blocked")
  void teachers() throws Exception {
    // Arrange
    Teacher teo = new Teacher("it.block.teo@example.com", "it.block.teo@studymatch.com", "Teo Block Test",
        passwordEncoder.encode(PASSWORD));
    teo.setMustChangePassword(false);
    teo = teacherRepository.save(teo);

    // Act + Assert
    block(teo.getNumber(), anaSession).andExpect(status().isNoContent());
    block(ana.getNumber(), loginAs(teo.getInstitutionalEmail())).andExpect(status().isNoContent());
  }

  @Test
  @DisplayName("Staff don't have chats, so they can't be blocked: 404")
  void blockStaff() throws Exception {
    // Arrange
    Staff staff = staffRepository.save(new Staff("it.block.staff@example.com", "it.block.staff@studymatch.com",
        "Staff Block Test", passwordEncoder.encode(PASSWORD)));

    // Act + Assert
    block(staff.getNumber(), anaSession)
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
  }

  @Test
  @DisplayName("A number nobody has is 404")
  void blockMissing() throws Exception {
    // Act + Assert
    block(eva.getNumber() + 1000, anaSession).andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("Staff can't block anyone: 403")
  void staffForbidden() throws Exception {
    // Arrange
    Staff staff = new Staff("it.block.staff@example.com", "it.block.staff@studymatch.com", "Staff Block Test",
        passwordEncoder.encode(PASSWORD));
    staff.setMustChangePassword(false);
    staffRepository.save(staff);

    // Act + Assert
    block(rui.getNumber(), loginAs(staff.getInstitutionalEmail())).andExpect(status().isForbidden());
  }

  // the blocker's side

  @Test
  @DisplayName("The DM leaves the blocker's chat list and unread count while the block lasts (GR9)")
  void dmHiddenFromBlocker() throws Exception {
    // Act
    block(rui.getNumber(), anaSession).andExpect(status().isNoContent());

    // Assert
    mockMvc.perform(get("/chats/").session(anaSession))
        .andExpect(jsonPath("$.length()").value(0));
    mockMvc.perform(get("/chats/unread-count").session(anaSession))
        .andExpect(content().string("0"));
  }

  @Test
  @DisplayName("The blocker can't open the DM by its id either: 404, like a chat they're not in")
  void blockerCantOpenTheDm() throws Exception {
    // Arrange
    block(rui.getNumber(), anaSession).andExpect(status().isNoContent());

    // Act + Assert
    mockMvc.perform(get("/chats/" + withRui.getId() + "/messages").session(anaSession))
        .andExpect(status().isNotFound());
    sendTo(withRui.getId(), "hi", anaSession).andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("Starting the DM again tells the blocker it's blocked, with no chat id")
  void directChatSaysBlocked() throws Exception {
    // Arrange
    block(rui.getNumber(), anaSession).andExpect(status().isNoContent());

    // Act + Assert
    mockMvc.perform(get("/chats/direct/" + rui.getNumber()).session(anaSession))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.blocked").value(true))
        .andExpect(jsonPath("$.chatId").isEmpty())
        .andExpect(jsonPath("$.contact.number").value(rui.getNumber()));
  }

  @Test
  @DisplayName("The blocker messaging the person they blocked is 409 UNBLOCK_TO_MESSAGE and nothing is sent")
  void blockerMustUnblock() throws Exception {
    // Arrange
    block(rui.getNumber(), anaSession).andExpect(status().isNoContent());
    long before = messageRepository.count();

    // Act
    sendDirect(rui.getNumber(), "hi", anaSession)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("UNBLOCK_TO_MESSAGE"));

    // Assert
    assertEquals(before, messageRepository.count());
  }

  @Test
  @DisplayName("The person you blocked still shows up in your contact search")
  void blockerStillFindsThem() throws Exception {
    // Arrange
    block(rui.getNumber(), anaSession).andExpect(status().isNoContent());

    // Act + Assert
    mockMvc.perform(get("/users/chat-contacts").session(anaSession).param("search", "Rui Block Test"))
        .andExpect(jsonPath("$.content.length()").value(1));
  }

  // the blocked user's side, nothing tells them

  @Test
  @DisplayName("The blocked user's message in the DM is sent and shows on their side as usual")
  void blockedSendsAsUsual() throws Exception {
    // Arrange
    block(rui.getNumber(), anaSession).andExpect(status().isNoContent());

    // Act
    sendTo(withRui.getId(), "are you there", ruiSession).andExpect(status().isCreated());

    // Assert
    mockMvc.perform(get("/chats/" + withRui.getId() + "/messages").session(ruiSession))
        .andExpect(jsonPath("$.messages.length()").value(2))
        .andExpect(jsonPath("$.messages[1].text").value("are you there"));
    mockMvc.perform(get("/chats/").session(ruiSession))
        .andExpect(jsonPath("$[0].lastMessage.text").value("are you there"));
  }

  @Test
  @DisplayName("Sending by number works the same for the blocked user")
  void blockedSendsDirect() throws Exception {
    // Arrange
    block(rui.getNumber(), anaSession).andExpect(status().isNoContent());

    // Act + Assert
    sendDirect(ana.getNumber(), "hello?", ruiSession).andExpect(status().isCreated());
  }

  @Test
  @DisplayName("The blocked user's view of the DM and the contact search don't change")
  void blockedSeesNothingNew() throws Exception {
    // Arrange
    block(rui.getNumber(), anaSession).andExpect(status().isNoContent());

    // Act + Assert
    mockMvc.perform(get("/chats/direct/" + ana.getNumber()).session(ruiSession))
        .andExpect(jsonPath("$.blocked").value(false))
        .andExpect(jsonPath("$.chatId").value(withRui.getId()));
    mockMvc.perform(get("/users/chat-contacts").session(ruiSession).param("search", "Ana Block Test"))
        .andExpect(jsonPath("$.content.length()").value(1));
  }

  // after unblocking

  @Test
  @DisplayName("Unblocking brings the DM back without what was sent during the block, and only old messages count")
  void unblockBringsTheDmBack() throws Exception {
    // Arrange
    block(rui.getNumber(), anaSession).andExpect(status().isNoContent());
    sendTo(withRui.getId(), "are you there", ruiSession).andExpect(status().isCreated());

    // Act
    unblock(rui.getNumber(), anaSession).andExpect(status().isNoContent());

    // Assert
    mockMvc.perform(get("/chats/" + withRui.getId() + "/messages").session(anaSession))
        .andExpect(jsonPath("$.messages.length()").value(1))
        .andExpect(jsonPath("$.messages[0].id").value(beforeBlock.getId()));
    mockMvc.perform(get("/chats/").session(anaSession))
        .andExpect(jsonPath("$[0].lastMessage.id").value(beforeBlock.getId()))
        .andExpect(jsonPath("$[0].unread").value(1));
    mockMvc.perform(get("/chats/unread-count").session(anaSession))
        .andExpect(content().string("1"));
  }

  @Test
  @DisplayName("After unblocking, new messages reach the blocker again")
  void afterUnblockMessagesArrive() throws Exception {
    // Arrange
    block(rui.getNumber(), anaSession).andExpect(status().isNoContent());
    unblock(rui.getNumber(), anaSession).andExpect(status().isNoContent());

    // Act
    sendTo(withRui.getId(), "back again", ruiSession).andExpect(status().isCreated());

    // Assert
    mockMvc.perform(get("/chats/").session(anaSession))
        .andExpect(jsonPath("$[0].lastMessage.text").value("back again"))
        .andExpect(jsonPath("$[0].unread").value(2));
  }

  @Test
  @DisplayName("A DM the blocked user started during the block never reaches the blocker, even after unblocking")
  void dmStartedDuringBlock() throws Exception {
    // Arrange
    block(eva.getNumber(), anaSession).andExpect(status().isNoContent());
    MockHttpSession evaSession = loginAs(eva.getInstitutionalEmail());
    sendDirect(ana.getNumber(), "hi ana", evaSession).andExpect(status().isCreated());

    // Act
    unblock(eva.getNumber(), anaSession).andExpect(status().isNoContent());

    // Assert
    mockMvc.perform(get("/chats/").session(anaSession))
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].chatId").value(withRui.getId()));
    mockMvc.perform(get("/chats/").session(evaSession))
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].lastMessage.text").value("hi ana"));
  }

}
