package com.studymatch.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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

import com.jayway.jsonpath.JsonPath;
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
class CreateGroupChatIntegrationTest {

  private static final String PASSWORD = "Test1234";

  // the 8-byte PNG signature, enough for the picture check
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
  private UserBlockRepository userBlockRepository;

  @Autowired
  private PasswordEncoder passwordEncoder;

  private String passwordHash;
  private Degree degree;
  private Student ana;
  private Student rui;
  private Student eva;
  private Teacher teo;
  private MockHttpSession anaSession;

  // nobody has a chat yet (rolled back after each test)
  @BeforeEach
  void setUp() throws Exception {
    passwordHash = passwordEncoder.encode(PASSWORD);
    degree = degreeRepository.save(new Degree("IT Group Chat Degree", 3));
    ana = saveStudent("it.groupchat.ana@example.com", "Ana Group Test");
    rui = saveStudent("it.groupchat.rui@example.com", "Rui Group Test");
    eva = saveStudent("it.groupchat.eva@example.com", "Eva Group Test");
    teo = new Teacher("it.groupchat.teo@example.com", "it.groupchat.teo@studymatch.com", "Teo Group Test",
        passwordHash);
    teo.setMustChangePassword(false);
    teo = teacherRepository.save(teo);
    anaSession = loginAs(ana.getInstitutionalEmail());
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

  private static String body(String name, String picture, List<Long> numbers) {
    String members = numbers.stream().map(String::valueOf).collect(Collectors.joining(","));
    String pictureJson = picture == null ? "null" : "\"" + picture + "\"";
    return "{\"name\":\"" + name + "\",\"picture\":" + pictureJson + ",\"memberNumbers\":[" + members + "]}";
  }

  private ResultActions create(MockHttpSession session, String body) throws Exception {
    return mockMvc.perform(post("/chats/groups").with(csrf()).session(session)
        .contentType(MediaType.APPLICATION_JSON)
        .content(body));
  }

  private CustomChat createdBy(Student owner) {
    return chatRepository.findCustomChatsOf(owner.getNumber()).getFirst();
  }

  // ana creates it, the id comes from the response
  private Long createdId(String body) throws Exception {
    String response = create(anaSession, body).andExpect(status().isCreated()).andReturn()
        .getResponse().getContentAsString();
    return ((Number) JsonPath.read(response, "$.chatId")).longValue();
  }

  // creating

  @Test
  @DisplayName("A group chat is created with the creator as owner, an empty row comes back")
  void created() throws Exception {
    // Act + Assert
    create(anaSession, body("Study group", null, List.of(rui.getNumber(), eva.getNumber())))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.name").value("Study group"))
        .andExpect(jsonPath("$.contact").isEmpty())
        .andExpect(jsonPath("$.picture").value(false))
        .andExpect(jsonPath("$.lastMessage").isEmpty())
        .andExpect(jsonPath("$.unread").value(0));
    CustomChat chat = createdBy(ana);
    assertEquals(ana.getNumber(), chat.getOwner().getNumber());
    assertEquals(List.of(ana.getNumber(), rui.getNumber(), eva.getNumber()),
        chat.getMembers().stream().map(member -> member.getNumber()).toList());
  }

  @Test
  @DisplayName("Teachers can be picked, and can create one too (GR8)")
  void teachersTakePart() throws Exception {
    // Arrange
    MockHttpSession teoSession = loginAs(teo.getInstitutionalEmail());

    // Act + Assert
    create(anaSession, body("With the teacher", null, List.of(rui.getNumber(), teo.getNumber())))
        .andExpect(status().isCreated());
    create(teoSession, body("Teo's group", null, List.of(ana.getNumber(), eva.getNumber())))
        .andExpect(status().isCreated());
  }

  @Test
  @DisplayName("The name can be up to 50 characters")
  void fiftyCharacterName() throws Exception {
    // Act + Assert
    create(anaSession, body("a".repeat(50), null, List.of(rui.getNumber(), eva.getNumber())))
        .andExpect(status().isCreated());
  }

  @Test
  @DisplayName("A name of 51 characters is refused")
  void longNameRefused() throws Exception {
    // Act + Assert
    create(anaSession, body("a".repeat(51), null, List.of(rui.getNumber(), eva.getNumber())))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.fieldErrors[0].field").value("name"));
  }

  @Test
  @DisplayName("A blank name is refused")
  void blankNameRefused() throws Exception {
    // Act + Assert
    create(anaSession, body("   ", null, List.of(rui.getNumber(), eva.getNumber())))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
  }

  @Test
  @DisplayName("Only 1 other person is refused, the same person twice still counts once (GR8)")
  void notEnoughMembers() throws Exception {
    // Act + Assert
    create(anaSession, body("Study group", null, List.of(rui.getNumber(), rui.getNumber())))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("NOT_ENOUGH_GROUP_CHAT_MEMBERS"))
        .andExpect(jsonPath("$.params.min").value(2));
  }

  @Test
  @DisplayName("20 others, 21 members with the creator, is refused (GR8)")
  void tooManyMembers() throws Exception {
    // Arrange
    List<Long> numbers = new ArrayList<>();
    for (int i = 0; i < CustomChat.MAX_MEMBERS; i++) {
      numbers.add(saveStudent("it.groupchat.many" + i + "@example.com", "Many " + i).getNumber());
    }

    // Act + Assert
    create(anaSession, body("Too big", null, numbers))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("TOO_MANY_GROUP_CHAT_MEMBERS"))
        .andExpect(jsonPath("$.params.max").value(CustomChat.MAX_MEMBERS));
  }

  @Test
  @DisplayName("Staff, an unknown number or the creator themselves can't be picked: NOT_FOUND")
  void notAChatContact() throws Exception {
    // Arrange
    Staff staff = new Staff("it.groupchat.staff@example.com", "it.groupchat.staff@studymatch.com", "Staff Group Test",
        passwordHash);
    staff = staffRepository.save(staff);

    // Act + Assert
    create(anaSession, body("Study group", null, List.of(rui.getNumber(), staff.getNumber())))
        .andExpect(status().isNotFound());
    create(anaSession, body("Study group", null, List.of(rui.getNumber(), 999_999L)))
        .andExpect(status().isNotFound());
    create(anaSession, body("Study group", null, List.of(rui.getNumber(), ana.getNumber())))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("Someone who blocked the creator can't be added, the error names them (GR9)")
  void blockerRefused() throws Exception {
    // Arrange
    userBlockRepository.save(new UserBlock(eva, ana));

    // Act + Assert
    create(anaSession, body("Study group", null, List.of(rui.getNumber(), eva.getNumber())))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CANNOT_ADD_TO_GROUP_CHAT"))
        .andExpect(jsonPath("$.params.numbers[0]").value(eva.getNumber()));
    assertEquals(0, chatRepository.findCustomChatsOf(ana.getNumber()).size());
  }

  @Test
  @DisplayName("Someone the creator blocked can still be added, a block only hides DMs (GR9)")
  void blockedByCreatorAdded() throws Exception {
    // Arrange
    userBlockRepository.save(new UserBlock(ana, eva));

    // Act + Assert
    create(anaSession, body("Study group", null, List.of(rui.getNumber(), eva.getNumber())))
        .andExpect(status().isCreated());
  }

  @Test
  @DisplayName("Staff can't create group chats: 403")
  void staffForbidden() throws Exception {
    // Arrange
    Staff staff = new Staff("it.groupchat.staff@example.com", "it.groupchat.staff@studymatch.com", "Staff Group Test",
        passwordHash);
    staff.setMustChangePassword(false);
    staffRepository.save(staff);
    MockHttpSession staffSession = loginAs(staff.getInstitutionalEmail());

    // Act + Assert
    create(staffSession, body("Study group", null, List.of(rui.getNumber(), eva.getNumber())))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("Creating a group chat without logging in is 401")
  void createNotLoggedIn() throws Exception {
    // Act + Assert
    mockMvc.perform(post("/chats/groups").with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content(body("Study group", null, List.of(rui.getNumber(), eva.getNumber()))))
        .andExpect(status().isUnauthorized());
  }

  // the chat list

  @Test
  @DisplayName("Every member sees the new group chat in their list before anyone writes")
  void listedForEveryMember() throws Exception {
    // Arrange
    create(anaSession, body("Study group", null, List.of(rui.getNumber(), eva.getNumber())));
    MockHttpSession ruiSession = loginAs(rui.getInstitutionalEmail());

    // Act + Assert
    mockMvc.perform(get("/chats/").session(ruiSession))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].name").value("Study group"))
        .andExpect(jsonPath("$[0].lastMessage").isEmpty())
        .andExpect(jsonPath("$[0].unread").value(0));
  }

  @Test
  @DisplayName("An empty group chat sits where it was made: after a newer message, before an older one")
  void emptyChatByCreation() throws Exception {
    // Arrange - a DM message, then the group chat, then another DM's message
    DirectChat withRui = chatRepository.save(new DirectChat(ana, rui));
    DirectChat withEva = chatRepository.save(new DirectChat(ana, eva));
    messageRepository.save(new Message(withRui, rui, "older"));
    Thread.sleep(5);
    create(anaSession, body("Study group", null, List.of(rui.getNumber(), eva.getNumber())));
    Thread.sleep(5);
    messageRepository.save(new Message(withEva, eva, "newer"));

    // Act + Assert
    mockMvc.perform(get("/chats/").session(anaSession))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(3))
        .andExpect(jsonPath("$[0].chatId").value(withEva.getId()))
        .andExpect(jsonPath("$[1].name").value("Study group"))
        .andExpect(jsonPath("$[2].chatId").value(withRui.getId()));
  }

  @Test
  @DisplayName("Members can write in it, and the others get it as unread")
  void membersCanWrite() throws Exception {
    // Arrange
    create(anaSession, body("Study group", null, List.of(rui.getNumber(), eva.getNumber())));
    Long chatId = createdBy(ana).getId();
    MockHttpSession evaSession = loginAs(eva.getInstitutionalEmail());

    // Act
    mockMvc.perform(post("/chats/" + chatId + "/messages").with(csrf()).session(anaSession)
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"text\":\"hi all\"}"))
        .andExpect(status().isCreated());

    // Assert
    mockMvc.perform(get("/chats/").session(evaSession))
        .andExpect(jsonPath("$[0].lastMessage.text").value("hi all"))
        .andExpect(jsonPath("$[0].unread").value(1));
  }

  // the picture

  @Test
  @DisplayName("A PNG picture is saved and comes back as the image itself, cached privately")
  void pictureServed() throws Exception {
    // Arrange
    create(anaSession, body("Study group", PNG, List.of(rui.getNumber(), eva.getNumber())))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.picture").value(true));
    Long chatId = createdBy(ana).getId();

    // Act + Assert
    MvcResult result = mockMvc.perform(get("/chats/" + chatId + "/picture").session(anaSession))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.IMAGE_PNG))
        .andExpect(header().string("Cache-Control", "max-age=300, private"))
        .andReturn();
    assertEquals(8, result.getResponse().getContentAsByteArray().length);
  }

  @Test
  @DisplayName("The picture without logging in is 401")
  void pictureNotLoggedIn() throws Exception {
    // Arrange
    Long chatId = createdId(body("Study group", PNG, List.of(rui.getNumber(), eva.getNumber())));

    // Act + Assert
    mockMvc.perform(get("/chats/" + chatId + "/picture"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("A picture that isn't a PNG or JPEG is refused")
  void invalidPictureRefused() throws Exception {
    // Act + Assert
    create(anaSession, body("Study group", "https://example.com/cat.png", List.of(rui.getNumber(), eva.getNumber())))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_GROUP_CHAT_PICTURE"));
  }

  @Test
  @DisplayName("No picture or a DM: NOT_FOUND, the frontend shows initials")
  void noPicture() throws Exception {
    // Arrange
    Long withoutPicture = createdId(body("No picture", null, List.of(rui.getNumber(), eva.getNumber())));
    Long direct = chatRepository.save(new DirectChat(ana, rui)).getId();

    // Act + Assert
    mockMvc.perform(get("/chats/" + withoutPicture + "/picture").session(anaSession))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    mockMvc.perform(get("/chats/" + direct + "/picture").session(anaSession))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("Someone who isn't in the group chat gets NOT_FOUND, even though it has a picture (GR8)")
  void pictureMembersOnly() throws Exception {
    // Arrange
    Long chatId = createdId(body("Study group", PNG, List.of(rui.getNumber(), eva.getNumber())));
    MockHttpSession teoSession = loginAs(teo.getInstitutionalEmail());

    // Act + Assert
    mockMvc.perform(get("/chats/" + chatId + "/picture").session(teoSession))
        .andExpect(status().isNotFound());
  }

}
