package com.studymatch.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
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

import com.studymatch.enums.AssignmentType;
import com.studymatch.enums.GroupStatus;
import com.studymatch.enums.Language;
import com.studymatch.models.AcademicYear;
import com.studymatch.models.Assignment;
import com.studymatch.models.Category;
import com.studymatch.models.Degree;
import com.studymatch.models.Enrolment;
import com.studymatch.models.GroupingRule;
import com.studymatch.models.Student;
import com.studymatch.models.Subject;
import com.studymatch.models.SubjectEdition;
import com.studymatch.models.Teacher;
import com.studymatch.repositories.AcademicYearRepository;
import com.studymatch.repositories.AssignmentRepository;
import com.studymatch.repositories.CategoryRepository;
import com.studymatch.repositories.DegreeRepository;
import com.studymatch.repositories.EnrolmentRepository;
import com.studymatch.repositories.GroupRepository;
import com.studymatch.repositories.StudentRepository;
import com.studymatch.repositories.SubjectEditionRepository;
import com.studymatch.repositories.SubjectRepository;
import com.studymatch.repositories.TeacherRepository;

// not @Transactional like the other integration tests: two requests on two threads only see committed data, so this
// one saves for real and deletes everything afterwards
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GenerateGroupsConcurrencyIntegrationTest {

  private static final String PASSWORD = "Test1234";
  private static final String TEACHER = "it.groupsrace.teacher@studymatch.com";

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

  private Teacher teacher;
  private Degree degree;
  private Category category;
  private Subject subject;
  private AcademicYear year;
  private SubjectEdition edition;
  private final List<Student> students = new ArrayList<>();
  private final List<Enrolment> enrolments = new ArrayList<>();
  private Assignment assignment;

  @BeforeEach
  void setUp() {
    Teacher newTeacher = new Teacher("it.groupsrace.teacher@example.com", TEACHER, "Race Teacher",
        passwordEncoder.encode(PASSWORD));
    newTeacher.setMustChangePassword(false);
    teacher = teacherRepository.save(newTeacher);
    degree = degreeRepository.save(new Degree("IT Groups Race Degree", 3));
    category = categoryRepository.save(new Category("IT Groups Race Category"));
    subject = subjectRepository.save(new Subject("ITGRACE1", "IT Groups Race Subject", 6, category));
    year = academicYearRepository.save(new AcademicYear(2096));
    SubjectEdition newEdition = new SubjectEdition(subject, year, Set.of(teacher));
    newEdition.addClassSection("T1");
    edition = subjectEditionRepository.save(newEdition);
    for (int i = 0; i < 8; i++) {
      Student student = new Student("it.groupsrace." + i + "@example.com", "Race " + i, "hash", degree, 1);
      student.setLanguages(Set.of(Language.PORTUGUESE));
      students.add(studentRepository.save(student));
      enrolments.add(enrolmentRepository.save(new Enrolment(students.getLast(),
          edition.getClassSections().iterator().next())));
    }
    assignment = assignmentRepository.save(new Assignment(edition, "Race Project", AssignmentType.PROJECT,
        LocalDate.of(2097, 1, 15), new BigDecimal("0.40"), new GroupingRule(3, 4, 5, false)));
  }

  // the reverse of the set-up, so nothing is left for the other tests
  @AfterEach
  void cleanUp() {
    for (GroupStatus status : GroupStatus.values()) {
      groupRepository.deleteAll(groupRepository.findByAssignmentAndStatusOrderByNumber(assignment, status));
    }
    assignmentRepository.delete(assignment);
    enrolmentRepository.deleteAll(enrolments);
    studentRepository.deleteAll(students);
    subjectEditionRepository.delete(edition);
    academicYearRepository.delete(year);
    subjectRepository.delete(subject);
    categoryRepository.delete(category);
    degreeRepository.delete(degree);
    teacherRepository.delete(teacher);
  }

  private MockHttpSession login() throws Exception {
    MvcResult result = mockMvc.perform(post("/users/login").with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"email\":\"" + TEACHER + "\",\"password\":\"" + PASSWORD + "\"}"))
        .andReturn();
    assertEquals(200, result.getResponse().getStatus());
    return (MockHttpSession) result.getRequest().getSession(false);
  }

  @Test
  @DisplayName("Two regenerations at the same moment both succeed, and one set of drafts is left")
  void doubleClickRegenerates() throws Exception {
    // Arrange - both requests wait at the gate, then go together
    MockHttpSession session = login();
    String url = "/subject-editions/" + edition.getId() + "/assignments/" + assignment.getId() + "/groups";
    CountDownLatch gate = new CountDownLatch(1);
    ExecutorService threads = Executors.newFixedThreadPool(2);
    List<Future<Integer>> statuses = new ArrayList<>();
    for (int i = 0; i < 2; i++) {
      statuses.add(threads.submit(() -> {
        gate.await();
        return mockMvc.perform(post(url).with(csrf()).session(session)).andReturn().getResponse().getStatus();
      }));
    }

    // Act
    gate.countDown();
    List<Integer> results = new ArrayList<>();
    for (Future<Integer> status : statuses) {
      results.add(status.get());
    }
    threads.shutdown();

    // Assert
    assertEquals(List.of(201, 201), results);
    assertEquals(2, groupRepository.findByAssignmentAndStatusOrderByNumber(assignment, GroupStatus.DRAFT).size());
  }

}
