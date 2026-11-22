package com.studymatch.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.AfterEach;
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

import com.jayway.jsonpath.JsonPath;
import com.studymatch.models.CustomChat;
import com.studymatch.models.Degree;
import com.studymatch.models.PersonalityProfile;
import com.studymatch.models.Student;
import com.studymatch.repositories.ChatRepository;
import com.studymatch.repositories.ChatSettingsRepository;
import com.studymatch.repositories.DegreeRepository;
import com.studymatch.repositories.StudentRepository;

// two members adding people at the same moment, so each request commits on its own (deleted after each test)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AddGroupChatMembersConcurrencyIntegrationTest {

  private static final String PASSWORD = "Test1234";

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private DegreeRepository degreeRepository;

  @Autowired
  private StudentRepository studentRepository;

  @Autowired
  private ChatRepository chatRepository;

  @Autowired
  private ChatSettingsRepository chatSettingsRepository;

  @Autowired
  private PasswordEncoder passwordEncoder;

  private String passwordHash;
  private Degree degree;
  private Student ana;
  private Student rui;
  private Student eva;
  private Student zeca;
  private Student lia;
  private CustomChat group;

  // ana owns a group chat with rui and eva, zeca and lia aren't in it
  @BeforeEach
  void setUp() {
    passwordHash = passwordEncoder.encode(PASSWORD);
    degree = degreeRepository.save(new Degree("IT Concurrent Members Degree", 3));
    ana = saveStudent("it.concmembers.ana@example.com", "Ana Concurrent Test");
    rui = saveStudent("it.concmembers.rui@example.com", "Rui Concurrent Test");
    eva = saveStudent("it.concmembers.eva@example.com", "Eva Concurrent Test");
    zeca = saveStudent("it.concmembers.zeca@example.com", "Zeca Concurrent Test");
    lia = saveStudent("it.concmembers.lia@example.com", "Lia Concurrent Test");
    group = chatRepository.save(new CustomChat("Study group", null, ana, List.of(rui, eva)));
  }

  @AfterEach
  void cleanUp() {
    chatSettingsRepository.deleteAll(chatSettingsRepository.findAll().stream()
        .filter(settings -> settings.getChat().getId().equals(group.getId()))
        .toList());
    chatRepository.deleteById(group.getId());
    studentRepository.deleteAll(List.of(ana, rui, eva, zeca, lia));
    degreeRepository.delete(degree);
  }

  private Student saveStudent(String personalEmail, String name) {
    Student student = new Student(personalEmail, name, passwordHash, degree, 1);
    student.setMustChangePassword(false);
    student.setPersonalityProfile(new PersonalityProfile(0.5, 0.5, 0.5, 0.5, 0.5));
    student = studentRepository.save(student);
    student.generateInstitutionalEmail("studymatch.com");
    return studentRepository.save(student);
  }

  private MockHttpSession loginAs(Student student) throws Exception {
    MvcResult result = mockMvc.perform(post("/users/login").with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"email\":\"" + student.getInstitutionalEmail() + "\",\"password\":\"" + PASSWORD + "\"}"))
        .andReturn();
    assertEquals(200, result.getResponse().getStatus());
    return (MockHttpSession) result.getRequest().getSession(false);
  }

  // both requests wait at the gate, then go together. the statuses in the order given
  private List<Integer> addAtOnce(MockHttpSession first, Long firstNumber, MockHttpSession second, Long secondNumber)
      throws Exception {
    CountDownLatch gate = new CountDownLatch(1);
    ExecutorService threads = Executors.newFixedThreadPool(2);
    List<Future<Integer>> statuses = new ArrayList<>();
    for (Object[] request : List.of(new Object[] { first, firstNumber }, new Object[] { second, secondNumber })) {
      statuses.add(threads.submit(() -> {
        gate.await();
        return mockMvc.perform(post("/chats/" + group.getId() + "/members").with(csrf())
            .session((MockHttpSession) request[0])
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"memberNumbers\":[" + request[1] + "]}"))
            .andReturn().getResponse().getStatus();
      }));
    }
    gate.countDown();
    List<Integer> results = new ArrayList<>();
    for (Future<Integer> status : statuses) {
      results.add(status.get());
    }
    threads.shutdown();
    return results;
  }

  private int memberCount(MockHttpSession session) throws Exception {
    String members = mockMvc.perform(get("/chats/" + group.getId() + "/members").session(session))
        .andReturn().getResponse().getContentAsString();
    return JsonPath.read(members, "$.length()");
  }

  @Test
  @DisplayName("Two members adding different people at the same moment both get in, nobody's lost")
  void differentPeopleAtOnce() throws Exception {
    // Arrange
    MockHttpSession anaSession = loginAs(ana);
    MockHttpSession ruiSession = loginAs(rui);

    // Act
    List<Integer> results = addAtOnce(anaSession, zeca.getNumber(), ruiSession, lia.getNumber());

    // Assert
    assertEquals(List.of(200, 200), results);
    assertEquals(5, memberCount(anaSession));
  }

  @Test
  @DisplayName("Two members adding the same person at the same moment: both fine, they're in once")
  void samePersonAtOnce() throws Exception {
    // Arrange
    MockHttpSession anaSession = loginAs(ana);
    MockHttpSession ruiSession = loginAs(rui);

    // Act
    List<Integer> results = addAtOnce(anaSession, zeca.getNumber(), ruiSession, zeca.getNumber());

    // Assert
    assertEquals(List.of(200, 200), results);
    assertEquals(4, memberCount(anaSession));
  }

}
