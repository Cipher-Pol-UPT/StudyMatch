package com.studymatch.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

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
import com.studymatch.models.Staff;
import com.studymatch.models.Student;
import com.studymatch.models.Teacher;
import com.studymatch.models.UserBlock;
import com.studymatch.repositories.ChatRepository;
import com.studymatch.repositories.DegreeRepository;
import com.studymatch.repositories.MessageRepository;
import com.studymatch.repositories.StaffRepository;
import com.studymatch.repositories.StudentRepository;
import com.studymatch.repositories.TeacherRepository;
import com.studymatch.repositories.UserBlockRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AddGroupChatMembersIntegrationTest {

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

  private String passwordHash;
  private Degree degree;
  private Student ana;
  private Student rui;
  private Student eva;
  private Student zeca;
  private Teacher teo;
  private CustomChat group;
  private MockHttpSession ruiSession;

  // ana owns a group chat with rui and eva, zeca and teo aren't in it (rolled back after each test)
  @BeforeEach
  void setUp() throws Exception {
    passwordHash = passwordEncoder.encode(PASSWORD);
    degree = degreeRepository.save(new Degree("IT Add Members Degree", 3));
    ana = saveStudent("it.addmembers.ana@example.com", "Ana Add Test");
    rui = saveStudent("it.addmembers.rui@example.com", "Rui Add Test");
    eva = saveStudent("it.addmembers.eva@example.com", "Eva Add Test");
    zeca = saveStudent("it.addmembers.zeca@example.com", "Zeca Add Test");
    teo = new Teacher("it.addmembers.teo@example.com", "it.addmembers.teo@studymatch.com", "Teo Add Test",
        passwordHash);
    teo.setMustChangePassword(false);
    teo = teacherRepository.save(teo);
    group = chatRepository.save(new CustomChat("Study group", null, ana, List.of(rui, eva)));
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

  private ResultActions add(MockHttpSession session, Long chatId, List<Long> numbers) throws Exception {
    String members = numbers.stream().map(String::valueOf).collect(Collectors.joining(","));
    return mockMvc.perform(post("/chats/" + chatId + "/members").with(csrf()).session(session)
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"memberNumbers\":[" + members + "]}"));
  }

  // adding

  @Test
  @DisplayName("Any member adds people, not only the owner, and gets the members back in joining order")
  void anyMemberAdds() throws Exception {
    // Act + Assert
    add(ruiSession, group.getId(), List.of(zeca.getNumber(), teo.getNumber()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(5))
        .andExpect(jsonPath("$[0].number").value(ana.getNumber()))
        .andExpect(jsonPath("$[3].number").value(zeca.getNumber()))
        .andExpect(jsonPath("$[4].number").value(teo.getNumber()))
        .andExpect(jsonPath("$[4].role").value("TEACHER"));
  }

  @Test
  @DisplayName("A new member gets the chat in their list with the whole history, already read (GR8)")
  void newMemberSeesHistoryRead() throws Exception {
    // Arrange
    messageRepository.save(new Message(group, ana, "before you came"));
    Thread.sleep(5);
    add(ruiSession, group.getId(), List.of(zeca.getNumber())).andExpect(status().isOk());
    MockHttpSession zecaSession = loginAs(zeca.getInstitutionalEmail());

    // Act + Assert
    mockMvc.perform(get("/chats/").session(zecaSession))
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].lastMessage.text").value("before you came"))
        .andExpect(jsonPath("$[0].unread").value(0));
    mockMvc.perform(get("/chats/" + group.getId() + "/messages").session(zecaSession))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.messages[0].text").value("before you came"));
  }

  @Test
  @DisplayName("What's sent after joining counts as unread for the new member")
  void laterMessagesUnread() throws Exception {
    // Arrange
    add(ruiSession, group.getId(), List.of(zeca.getNumber())).andExpect(status().isOk());
    Thread.sleep(5);
    messageRepository.save(new Message(group, ana, "welcome"));
    MockHttpSession zecaSession = loginAs(zeca.getInstitutionalEmail());

    // Act + Assert
    mockMvc.perform(get("/chats/").session(zecaSession))
        .andExpect(jsonPath("$[0].unread").value(1));
  }

  @Test
  @DisplayName("Adding someone already in changes nothing")
  void currentMemberSkipped() throws Exception {
    // Act + Assert
    add(ruiSession, group.getId(), List.of(eva.getNumber()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(3));
  }

  @Test
  @DisplayName("Going past 20 members is refused (GR8)")
  void tooManyMembers() throws Exception {
    // Arrange - 3 in already, 18 more makes 21
    List<Long> numbers = new ArrayList<>();
    for (int i = 0; i < CustomChat.MAX_MEMBERS - 2; i++) {
      numbers.add(saveStudent("it.addmembers.many" + i + "@example.com", "Many " + i).getNumber());
    }

    // Act + Assert
    add(ruiSession, group.getId(), numbers)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("TOO_MANY_GROUP_CHAT_MEMBERS"));
  }

  @Test
  @DisplayName("Someone who blocked the user adding them can't be added, the error names them (GR9)")
  void blockerRefused() throws Exception {
    // Arrange
    userBlockRepository.save(new UserBlock(zeca, rui));

    // Act + Assert
    add(ruiSession, group.getId(), List.of(zeca.getNumber()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CANNOT_ADD_TO_GROUP_CHAT"))
        .andExpect(jsonPath("$.params.numbers[0]").value(zeca.getNumber()));
  }

  @Test
  @DisplayName("A DM's members can't be changed: CHAT_MEMBERS_FIXED")
  void directChatRefused() throws Exception {
    // Arrange
    DirectChat direct = chatRepository.save(new DirectChat(rui, eva));

    // Act + Assert
    add(ruiSession, direct.getId(), List.of(zeca.getNumber()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CHAT_MEMBERS_FIXED"));
  }

  @Test
  @DisplayName("Someone who isn't in the group chat can't add anyone, not even themselves: NOT_FOUND (GR8)")
  void nonMemberNotFound() throws Exception {
    // Arrange
    MockHttpSession zecaSession = loginAs(zeca.getInstitutionalEmail());

    // Act + Assert
    add(zecaSession, group.getId(), List.of(teo.getNumber()))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("Staff or an unknown number can't be added: NOT_FOUND")
  void notAChatContact() throws Exception {
    // Arrange
    Staff staff = staffRepository.save(new Staff("it.addmembers.staff@example.com",
        "it.addmembers.staff@studymatch.com", "Staff Add Test", passwordHash));

    // Act + Assert
    add(ruiSession, group.getId(), List.of(staff.getNumber()))
        .andExpect(status().isNotFound());
    add(ruiSession, group.getId(), List.of(999_999L))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("An empty list is refused")
  void emptyListRefused() throws Exception {
    // Act + Assert
    add(ruiSession, group.getId(), List.of())
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
  }

  @Test
  @DisplayName("Staff can't add members: 403")
  void staffForbidden() throws Exception {
    // Arrange
    Staff staff = new Staff("it.addmembers.staff@example.com", "it.addmembers.staff@studymatch.com",
        "Staff Add Test", passwordHash);
    staff.setMustChangePassword(false);
    staffRepository.save(staff);
    MockHttpSession staffSession = loginAs(staff.getInstitutionalEmail());

    // Act + Assert
    add(staffSession, group.getId(), List.of(zeca.getNumber()))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("Adding members without logging in is 401")
  void addNotLoggedIn() throws Exception {
    // Act + Assert
    mockMvc.perform(post("/chats/" + group.getId() + "/members").with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"memberNumbers\":[" + zeca.getNumber() + "]}"))
        .andExpect(status().isUnauthorized());
  }

  // the member list

  @Test
  @DisplayName("The member list without logging in is 401")
  void membersNotLoggedIn() throws Exception {
    // Act + Assert
    mockMvc.perform(get("/chats/" + group.getId() + "/members"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("A member sees who's in the group chat, the owner first")
  void membersListed() throws Exception {
    // Act + Assert
    mockMvc.perform(get("/chats/" + group.getId() + "/members").session(ruiSession))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(3))
        .andExpect(jsonPath("$[0].name").value("Ana Add Test"))
        .andExpect(jsonPath("$[1].name").value("Rui Add Test"))
        .andExpect(jsonPath("$[2].name").value("Eva Add Test"));
  }

  @Test
  @DisplayName("A DM's members are the two people")
  void directChatMembers() throws Exception {
    // Arrange
    DirectChat direct = chatRepository.save(new DirectChat(rui, eva));

    // Act + Assert
    mockMvc.perform(get("/chats/" + direct.getId() + "/members").session(ruiSession))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2));
  }

  @Test
  @DisplayName("Someone who isn't in it can't see who is: NOT_FOUND (GR8)")
  void membersOnly() throws Exception {
    // Arrange
    MockHttpSession zecaSession = loginAs(zeca.getInstitutionalEmail());

    // Act + Assert
    mockMvc.perform(get("/chats/" + group.getId() + "/members").session(zecaSession))
        .andExpect(status().isNotFound());
  }

}
