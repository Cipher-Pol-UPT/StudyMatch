package com.studymatch.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

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

import com.jayway.jsonpath.JsonPath;
import com.studymatch.models.Admin;
import com.studymatch.models.Degree;
import com.studymatch.models.DirectChat;
import com.studymatch.models.PersonalityProfile;
import com.studymatch.models.Staff;
import com.studymatch.models.Student;
import com.studymatch.models.Teacher;
import com.studymatch.repositories.AdminRepository;
import com.studymatch.repositories.ChatRepository;
import com.studymatch.repositories.DegreeRepository;
import com.studymatch.repositories.StaffRepository;
import com.studymatch.repositories.StudentRepository;
import com.studymatch.repositories.TeacherRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class StartDirectChatIntegrationTest {

  private static final String PASSWORD = "Test1234";
  private static final String SEARCH = "DM Test";

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
  private AdminRepository adminRepository;

  @Autowired
  private ChatRepository chatRepository;

  @Autowired
  private PasswordEncoder passwordEncoder;

  private Degree degree;
  private Student ana;
  private Student rui;
  private Student eva;
  private Student bia;
  private Teacher teo;
  private Staff staff;
  private Admin admin;
  private DirectChat anaAndRui;

  // Ana already has a DM with Rui, not with Eva. Bia is blocked by an Admin (rolled back after each test)
  @BeforeEach
  void setUp() {
    degree = degreeRepository.save(new Degree("IT DM Degree", 3));
    ana = saveStudent("it.dm.ana@example.com", "Ana DM Test");
    rui = saveStudent("it.dm.rui@example.com", "Rui DM Test");
    eva = saveStudent("it.dm.eva@example.com", "Eva DM Test");
    bia = saveStudent("it.dm.bia@example.com", "Bia DM Test");
    bia.block("test reason");
    teo = new Teacher("it.dm.teo@example.com", "it.dm.teo@studymatch.com", "Teo DM Test",
        passwordEncoder.encode(PASSWORD));
    teo.setMustChangePassword(false);
    teo = teacherRepository.save(teo);
    staff = new Staff("it.dm.staff@example.com", "it.dm.staff@studymatch.com", "Staff DM Test",
        passwordEncoder.encode(PASSWORD));
    staff.setMustChangePassword(false);
    staff = staffRepository.save(staff);
    admin = adminRepository.save(new Admin("it.dm.admin@example.com", "it.dm.admin@studymatch.com",
        "Admin DM Test", passwordEncoder.encode(PASSWORD)));
    anaAndRui = chatRepository.save(new DirectChat(ana, rui));
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

  private static List<String> names(MvcResult result) throws Exception {
    return JsonPath.read(result.getResponse().getContentAsString(), "$.content[*].name");
  }

  // search

  @Test
  @DisplayName("A Student finds Students and Teachers by name, sorted, without themselves, Staff, Admin or blocked accounts")
  void studentSearchesByName() throws Exception {
    // Arrange
    MockHttpSession session = loginAs(ana.getInstitutionalEmail());

    // Act
    MvcResult result = mockMvc.perform(get("/users/chat-contacts").session(session).param("search", SEARCH))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(3))
        .andExpect(jsonPath("$.content[2].number").value(teo.getNumber()))
        .andExpect(jsonPath("$.content[2].role").value("TEACHER"))
        .andReturn();

    // Assert
    assertEquals(List.of("Eva DM Test", "Rui DM Test", "Teo DM Test"), names(result));
  }

  @Test
  @DisplayName("A Teacher can search too, and finds the Students but not themselves")
  void teacherSearches() throws Exception {
    // Arrange
    MockHttpSession session = loginAs(teo.getInstitutionalEmail());

    // Act
    MvcResult result = mockMvc.perform(get("/users/chat-contacts").session(session).param("search", SEARCH))
        .andExpect(status().isOk())
        .andReturn();

    // Assert
    assertEquals(List.of("Ana DM Test", "Eva DM Test", "Rui DM Test"), names(result));
  }

  @Test
  @DisplayName("Searching the exact number finds that person")
  void searchByNumber() throws Exception {
    // Arrange
    MockHttpSession session = loginAs(ana.getInstitutionalEmail());

    // Act
    MvcResult result = mockMvc.perform(get("/users/chat-contacts").session(session)
        .param("search", rui.getNumber().toString()))
        .andExpect(status().isOk())
        .andReturn();

    // Assert
    assertEquals(List.of("Rui DM Test"), names(result));
  }

  @Test
  @DisplayName("Searching your own number finds nobody")
  void searchOwnNumber() throws Exception {
    // Arrange
    MockHttpSession session = loginAs(ana.getInstitutionalEmail());

    // Act + Assert
    mockMvc.perform(get("/users/chat-contacts").session(session).param("search", ana.getNumber().toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(0));
  }

  @Test
  @DisplayName("Results come 20 per page")
  void pageSize() throws Exception {
    // Arrange
    MockHttpSession session = loginAs(ana.getInstitutionalEmail());

    // Act + Assert
    mockMvc.perform(get("/users/chat-contacts").session(session).param("search", SEARCH).param("page", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.size").value(20))
        .andExpect(jsonPath("$.page").value(1))
        .andExpect(jsonPath("$.content").isEmpty());
  }

  @Test
  @DisplayName("Staff can't search for chat contacts (403 ACCESS_DENIED)")
  void staffCantSearch() throws Exception {
    // Arrange
    MockHttpSession session = loginAs(staff.getInstitutionalEmail());

    // Act + Assert
    mockMvc.perform(get("/users/chat-contacts").session(session))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
  }

  @Test
  @DisplayName("Searching without logging in is 401")
  void searchNotLoggedIn() throws Exception {
    mockMvc.perform(get("/users/chat-contacts"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
  }

  // DM lookup

  @Test
  @DisplayName("With a DM already, both sides get its id and the other person")
  void existingDirectChat() throws Exception {
    // Arrange
    MockHttpSession anaSession = loginAs(ana.getInstitutionalEmail());
    MockHttpSession ruiSession = loginAs(rui.getInstitutionalEmail());

    // Act + Assert
    mockMvc.perform(get("/chats/direct/{number}", rui.getNumber()).session(anaSession))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.chatId").value(anaAndRui.getId()))
        .andExpect(jsonPath("$.contact.name").value("Rui DM Test"));
    mockMvc.perform(get("/chats/direct/{number}", ana.getNumber()).session(ruiSession))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.chatId").value(anaAndRui.getId()))
        .andExpect(jsonPath("$.contact.name").value("Ana DM Test"));
  }

  @Test
  @DisplayName("Without a DM the chat id is null and nothing is created, the first message does that")
  void noDirectChatYet() throws Exception {
    // Arrange
    MockHttpSession session = loginAs(ana.getInstitutionalEmail());
    long chatsBefore = chatRepository.count();

    // Act
    mockMvc.perform(get("/chats/direct/{number}", eva.getNumber()).session(session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.chatId").isEmpty())
        .andExpect(jsonPath("$.contact.number").value(eva.getNumber()))
        .andExpect(jsonPath("$.contact.role").value("STUDENT"));

    // Assert
    assertEquals(chatsBefore, chatRepository.count());
  }

  @Test
  @DisplayName("A Teacher is someone you can message too")
  void directChatWithTeacher() throws Exception {
    // Arrange
    MockHttpSession session = loginAs(ana.getInstitutionalEmail());

    // Act + Assert
    mockMvc.perform(get("/chats/direct/{number}", teo.getNumber()).session(session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.chatId").isEmpty())
        .andExpect(jsonPath("$.contact.role").value("TEACHER"));
  }

  @Test
  @DisplayName("Yourself, Staff, Admin, a blocked account or a number nobody has is 404 NOT_FOUND")
  void notSomeoneYouCanMessage() throws Exception {
    // Arrange
    MockHttpSession session = loginAs(ana.getInstitutionalEmail());
    List<Long> numbers = List.of(ana.getNumber(), staff.getNumber(), admin.getNumber(), bia.getNumber(), -1L);

    // Act + Assert
    for (Long number : numbers) {
      mockMvc.perform(get("/chats/direct/{number}", number).session(session))
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }
  }

  @Test
  @DisplayName("Staff can't open a DM (403 ACCESS_DENIED)")
  void staffCantOpenDirectChat() throws Exception {
    // Arrange
    MockHttpSession session = loginAs(staff.getInstitutionalEmail());

    // Act + Assert
    mockMvc.perform(get("/chats/direct/{number}", ana.getNumber()).session(session))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
  }

}
