package com.studymatch.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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

import com.studymatch.enums.AssignmentType;
import com.studymatch.enums.GroupStatus;
import com.studymatch.models.AcademicYear;
import com.studymatch.models.Assignment;
import com.studymatch.models.Category;
import com.studymatch.models.Degree;
import com.studymatch.models.Enrolment;
import com.studymatch.models.Group;
import com.studymatch.models.GroupChat;
import com.studymatch.models.GroupingRule;
import com.studymatch.models.Message;
import com.studymatch.models.PersonalityProfile;
import com.studymatch.models.Student;
import com.studymatch.models.Subject;
import com.studymatch.models.SubjectEdition;
import com.studymatch.models.Teacher;
import com.studymatch.repositories.AcademicYearRepository;
import com.studymatch.repositories.AssignmentRepository;
import com.studymatch.repositories.CategoryRepository;
import com.studymatch.repositories.ChatRepository;
import com.studymatch.repositories.DegreeRepository;
import com.studymatch.repositories.EnrolmentRepository;
import com.studymatch.repositories.GroupRepository;
import com.studymatch.repositories.MessageRepository;
import com.studymatch.repositories.StudentRepository;
import com.studymatch.repositories.SubjectEditionRepository;
import com.studymatch.repositories.SubjectRepository;
import com.studymatch.repositories.TeacherRepository;

import jakarta.persistence.EntityManager;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AssignmentGroupChatIntegrationTest {

  private static final String PASSWORD = "Test1234";
  private static final String TEACHER = "it.groupchat.teacher@studymatch.com";

  @Autowired private MockMvc mockMvc;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private TeacherRepository teacherRepository;
  @Autowired private DegreeRepository degreeRepository;
  @Autowired private CategoryRepository categoryRepository;
  @Autowired private SubjectRepository subjectRepository;
  @Autowired private AcademicYearRepository academicYearRepository;
  @Autowired private SubjectEditionRepository subjectEditionRepository;
  @Autowired private StudentRepository studentRepository;
  @Autowired private EnrolmentRepository enrolmentRepository;
  @Autowired private AssignmentRepository assignmentRepository;
  @Autowired private GroupRepository groupRepository;
  @Autowired private ChatRepository chatRepository;
  @Autowired private MessageRepository messageRepository;
  @Autowired private EntityManager entityManager;

  private SubjectEdition edition;
  private Assignment assignment;
  private List<Student> students;

  // four students who can log in, a project for groups of 2 to 3 and two draft groups: 0 and 1, 2 and 3
  @BeforeEach
  void setUp() {
    Teacher teacher = new Teacher("it.groupchat.teacher@example.com", TEACHER, "Test Teacher",
        passwordEncoder.encode(PASSWORD));
    teacher.setMustChangePassword(false);
    teacher = teacherRepository.save(teacher);

    Degree degree = degreeRepository.save(new Degree("IT Group Chat Degree", 3));
    Category category = categoryRepository.save(new Category("IT Group Chat Category"));
    Subject subject = subjectRepository.save(new Subject("ITGRPCHAT", "IT Group Chat Subject", 6, category));
    AcademicYear year = academicYearRepository.save(new AcademicYear(2094));
    edition = new SubjectEdition(subject, year, Set.of(teacher));
    edition.addClassSection("T1");
    edition = subjectEditionRepository.save(edition);

    students = new ArrayList<>();
    for (int i = 0; i < 4; i++) {
      Student student = saveStudent("it.groupchat." + i + "@example.com", "Student " + i, degree);
      enrolmentRepository.save(new Enrolment(student, edition.getClassSections().iterator().next()));
      students.add(student);
    }
    assignment = assignmentRepository.save(new Assignment(edition, "Project", AssignmentType.PROJECT,
        LocalDate.of(2095, 1, 15), new BigDecimal("0.40"), new GroupingRule(2, 2, 3, false)));
    drafts(new int[] { 0, 1 }, new int[] { 2, 3 });
  }

  private Student saveStudent(String personalEmail, String name, Degree degree) {
    Student student = new Student(personalEmail, name, passwordEncoder.encode(PASSWORD), degree, 1);
    student.setMustChangePassword(false);
    student.setPersonalityProfile(new PersonalityProfile(0.5, 0.5, 0.5, 0.5, 0.5));
    student = studentRepository.save(student);
    student.generateInstitutionalEmail("studymatch.com");
    return studentRepository.save(student);
  }

  // draft groups numbered from 1, each with the students at these positions
  private void drafts(int[]... positions) {
    int number = 1;
    for (int[] members : positions) {
      Group group = new Group(assignment, number++);
      for (int position : members) {
        group.addMember(students.get(position));
      }
      groupRepository.save(group);
    }
  }

  private MockHttpSession loginAs(String email) throws Exception {
    MvcResult result = mockMvc.perform(post("/users/login").with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
        .andReturn();
    assertEquals(200, result.getResponse().getStatus());
    return (MockHttpSession) result.getRequest().getSession(false);
  }

  private MockHttpSession studentSession(int position) throws Exception {
    return loginAs(students.get(position).getInstitutionalEmail());
  }

  private void publish() throws Exception {
    mockMvc.perform(post(groupsUrl() + "/publish").with(csrf()).session(loginAs(TEACHER)))
        .andExpect(status().isNoContent());
  }

  private ResultActions editPublished(String... groups) throws Exception {
    return mockMvc.perform(put(groupsUrl() + "/published").with(csrf()).session(loginAs(TEACHER))
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"groups\":[" + String.join(",", groups) + "]}"));
  }

  private String groupsUrl() {
    return "/subject-editions/" + edition.getId() + "/assignments/" + assignment.getId() + "/groups";
  }

  // one group of the request, an id of null is a new group
  private String group(Long id, int... positions) {
    return "{\"id\":" + id + ",\"members\":["
        + IntStream.of(positions).mapToObj(position -> students.get(position).getNumber().toString())
            .collect(Collectors.joining(","))
        + "]}";
  }

  // the chat of the published group with that number
  private GroupChat chatOf(int number) {
    Group group = groupRepository.findByAssignmentAndStatusOrderByNumber(assignment, GroupStatus.PUBLISHED)
        .get(number - 1);
    return chatRepository.findGroupChatsFor(List.of(group)).getFirst();
  }

  // a message saved straight away, then forgotten so the next request reads the database
  private Long message(GroupChat chat, int author, String text) {
    Long id = messageRepository.save(new Message(chat, students.get(author), text)).getId();
    forget();
    return id;
  }

  // writes what's pending, like the commit would, and reads everything from the database again
  private void forget() {
    entityManager.flush();
    entityManager.clear();
  }

  @Test
  @DisplayName("Publishing gives each group a chat on its students' list, named after the assignment, the teacher has none")
  void publishCreatesChats() throws Exception {
    // Act
    publish();

    // Assert
    mockMvc.perform(get("/chats/").session(studentSession(0)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].name").value("Project · IT Group Chat Subject · Group 1"))
        .andExpect(jsonPath("$[0].assignment").value(true))
        .andExpect(jsonPath("$[0].contact").doesNotExist())
        .andExpect(jsonPath("$[0].unread").value(0));
    mockMvc.perform(get("/chats/").session(studentSession(2)))
        .andExpect(jsonPath("$[0].name").value("Project · IT Group Chat Subject · Group 2"));
    mockMvc.perform(get("/chats/").session(loginAs(TEACHER)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
  }

  @Test
  @DisplayName("Another group's chat is NOT_FOUND, like any chat you're not in (GR8)")
  void otherGroupNotFound() throws Exception {
    // Arrange
    publish();

    // Act + Assert
    mockMvc.perform(get("/chats/" + chatOf(2).getId() + "/messages").session(studentSession(0)))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("Adding, removing and leaving are 409 CHAT_MEMBERS_FIXED, the members come from the group")
  void membersFixed() throws Exception {
    // Arrange
    publish();
    Long chatId = chatOf(1).getId();
    MockHttpSession session = studentSession(0);

    // Act + Assert
    mockMvc.perform(post("/chats/" + chatId + "/members").with(csrf()).session(session)
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"memberNumbers\":[" + students.get(2).getNumber() + "]}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CHAT_MEMBERS_FIXED"));
    mockMvc.perform(post("/chats/" + chatId + "/leave").with(csrf()).session(session))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CHAT_MEMBERS_FIXED"));
  }

  @Test
  @DisplayName("A moved student loses the old group's chat and sees the new one with its whole history")
  void movedStudentSwapsChats() throws Exception {
    // Arrange
    publish();
    GroupChat first = chatOf(1);
    GroupChat second = chatOf(2);
    message(second, 2, "Before you came");
    List<Long> groupIds = List.of(first.getGroup().getId(), second.getGroup().getId());

    // Act - student 1 moves to group 2
    editPublished(group(groupIds.get(0), 0), group(groupIds.get(1), 1, 2, 3)).andExpect(status().isOk());

    // Assert
    MockHttpSession moved = studentSession(1);
    mockMvc.perform(get("/chats/" + first.getId() + "/messages").session(moved))
        .andExpect(status().isNotFound());
    mockMvc.perform(get("/chats/" + second.getId() + "/messages").session(moved))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.messages[0].text").value("Before you came"));
    mockMvc.perform(get("/chats/").session(moved))
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].chatId").value(second.getId()))
        .andExpect(jsonPath("$[0].unread").value(0));
  }

  @Test
  @DisplayName("A removed group's chat is deleted with its messages, a new group gets its own")
  void removedGroupLosesChat() throws Exception {
    // Arrange
    publish();
    GroupChat first = chatOf(1);
    GroupChat second = chatOf(2);
    Long messageId = message(second, 2, "Gone soon");

    // Act - group 2 goes, its students go to a new group
    editPublished(group(first.getGroup().getId(), 0, 1), group(null, 2, 3)).andExpect(status().isOk());
    forget();

    // Assert
    assertTrue(chatRepository.findById(second.getId()).isEmpty());
    assertTrue(messageRepository.findById(messageId).isEmpty());
    assertTrue(chatRepository.findById(first.getId()).isPresent());
    GroupChat fresh = chatOf(2);
    assertEquals(Set.of(students.get(2).getNumber(), students.get(3).getNumber()),
        fresh.getGroup().getMembers().stream().map(Student::getNumber).collect(Collectors.toSet()));
  }

  @Test
  @DisplayName("Publishing new groups again replaces the old chats, the history goes with the old groups")
  void republishReplacesChats() throws Exception {
    // Arrange
    publish();
    GroupChat old = chatOf(1);
    Long messageId = message(old, 0, "Old group");
    drafts(new int[] { 0, 2 }, new int[] { 1, 3 });

    // Act
    publish();
    forget();

    // Assert
    assertTrue(chatRepository.findById(old.getId()).isEmpty());
    assertTrue(messageRepository.findById(messageId).isEmpty());
    mockMvc.perform(get("/chats/").session(studentSession(0)))
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].lastMessage").doesNotExist());
  }

}
