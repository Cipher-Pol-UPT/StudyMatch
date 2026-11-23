package com.studymatch.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import jakarta.persistence.EntityManager;

import org.hamcrest.Matchers;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
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
import com.studymatch.enums.AssignmentType;
import com.studymatch.enums.Language;
import com.studymatch.enums.StatuteType;
import com.studymatch.models.AcademicYear;
import com.studymatch.models.Assignment;
import com.studymatch.models.Category;
import com.studymatch.models.Degree;
import com.studymatch.models.Enrolment;
import com.studymatch.models.Group;
import com.studymatch.models.GroupingRule;
import com.studymatch.models.Staff;
import com.studymatch.models.Student;
import com.studymatch.models.Subject;
import com.studymatch.models.SubjectEdition;
import com.studymatch.models.Teacher;
import com.studymatch.models.User;
import com.studymatch.repositories.AcademicYearRepository;
import com.studymatch.repositories.AssignmentRepository;
import com.studymatch.repositories.CategoryRepository;
import com.studymatch.repositories.DegreeRepository;
import com.studymatch.repositories.EnrolmentRepository;
import com.studymatch.repositories.GroupRepository;
import com.studymatch.repositories.StaffRepository;
import com.studymatch.repositories.StudentRepository;
import com.studymatch.repositories.SubjectEditionRepository;
import com.studymatch.repositories.SubjectRepository;
import com.studymatch.repositories.TeacherRepository;

@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ViewGroupsIntegrationTest {

  private static final String PASSWORD = "Test1234";
  private static final String TEACHER = "it.viewgroups.teacher@studymatch.com";
  private static final String OTHER_TEACHER = "it.viewgroups.other@studymatch.com";
  private static final String STAFF = "it.viewgroups.staff@studymatch.com";

  @Autowired private MockMvc mockMvc;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private TeacherRepository teacherRepository;
  @Autowired private StaffRepository staffRepository;
  @Autowired private DegreeRepository degreeRepository;
  @Autowired private CategoryRepository categoryRepository;
  @Autowired private SubjectRepository subjectRepository;
  @Autowired private AcademicYearRepository academicYearRepository;
  @Autowired private SubjectEditionRepository subjectEditionRepository;
  @Autowired private StudentRepository studentRepository;
  @Autowired private EnrolmentRepository enrolmentRepository;
  @Autowired private AssignmentRepository assignmentRepository;
  @Autowired private GroupRepository groupRepository;
  @Autowired private EntityManager entityManager;

  private SubjectEdition edition;
  private Degree degree;
  private Student ana;
  private Student bruno;
  private Student carla;
  private Student duarte;

  // four students: Ana, Bruno and Duarte in T1, Carla in T2
  @BeforeEach
  void setUp() {
    Teacher teacher = teacherRepository.save(withoutPasswordChange(new Teacher("it.viewgroups.teacher@example.com",
        TEACHER, "Test Teacher", passwordEncoder.encode(PASSWORD))));
    teacherRepository.save(withoutPasswordChange(new Teacher("it.viewgroups.other@example.com", OTHER_TEACHER,
        "Other Teacher", passwordEncoder.encode(PASSWORD))));
    staffRepository.save(withoutPasswordChange(new Staff("it.viewgroups.staff@example.com", STAFF, "Test Staff",
        passwordEncoder.encode(PASSWORD))));

    degree = degreeRepository.save(new Degree("IT View Groups Degree", 3));
    Category category = categoryRepository.save(new Category("IT View Groups Category"));
    Subject subject = subjectRepository.save(new Subject("ITVIEWGRP1", "IT View Groups Subject", 6, category));
    AcademicYear year = academicYearRepository.save(new AcademicYear(2093));
    edition = new SubjectEdition(subject, year, Set.of(teacher));
    edition.addClassSection("T1");
    edition.addClassSection("T2");
    edition = subjectEditionRepository.save(edition);

    ana = enrol(student("Ana", Set.of(Language.PORTUGUESE, Language.ENGLISH)), "T1");
    bruno = enrol(student("Bruno", Set.of(Language.ENGLISH)), "T1");
    carla = enrol(student("Carla", Set.of(Language.PORTUGUESE, Language.ENGLISH)), "T2");
    duarte = enrol(student("Duarte", Set.of(Language.PORTUGUESE)), "T1");
  }

  private Student student(String name, Set<Language> languages) {
    Student student = new Student("it.viewgroups." + name.toLowerCase() + "@example.com", name, "hash", degree, 1);
    student.setLanguages(languages);
    return studentRepository.save(student);
  }

  private Student enrol(Student student, String classSection) {
    enrolmentRepository.save(new Enrolment(student, edition.getClassSection(classSection)));
    return student;
  }

  private static <T extends User> T withoutPasswordChange(T user) {
    user.setMustChangePassword(false);
    return user;
  }

  private Assignment project() {
    return assignmentRepository.save(new Assignment(edition, "Project", AssignmentType.PROJECT,
        LocalDate.of(2094, 1, 15), new BigDecimal("0.40"), new GroupingRule(2, 2, 3, false)));
  }

  private Group group(Assignment assignment, int number, boolean published, Student... members) {
    Group group = new Group(assignment, number);
    for (Student member : members) {
      group.addMember(member);
    }
    if (published) {
      group.publish();
    }
    return groupRepository.save(group);
  }

  private MockHttpSession loginAs(String email) throws Exception {
    MvcResult result = mockMvc.perform(post("/users/login").with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
        .andReturn();
    assertEquals(200, result.getResponse().getStatus());
    return (MockHttpSession) result.getRequest().getSession(false);
  }

  private ResultActions viewGroups(String email, Long editionId, Long assignmentId) throws Exception {
    return mockMvc.perform(get("/subject-editions/" + editionId + "/assignments/" + assignmentId + "/groups")
        .session(loginAs(email)));
  }

  private ResultActions viewGroups(Assignment assignment) throws Exception {
    return viewGroups(TEACHER, edition.getId(), assignment.getId());
  }

  @Test
  @DisplayName("Published groups come with their members by name, each one's class and the languages they share")
  void publishedGroups() throws Exception {
    // Arrange
    Assignment assignment = project();
    Group first = group(assignment, 1, true, carla, ana);
    group(assignment, 2, true, duarte, bruno);

    // Act + Assert
    viewGroups(assignment)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.draft.length()").value(0))
        .andExpect(jsonPath("$.published.length()").value(2))
        .andExpect(jsonPath("$.published[0].id").value(first.getId()))
        .andExpect(jsonPath("$.published[0].number").value(1))
        .andExpect(jsonPath("$.published[0].status").value("PUBLISHED"))
        .andExpect(jsonPath("$.published[0].size").value(2))
        .andExpect(jsonPath("$.published[0].sharedLanguages", Matchers.containsInAnyOrder("PORTUGUESE", "ENGLISH")))
        .andExpect(jsonPath("$.published[0].members[0].name").value("Ana"))
        .andExpect(jsonPath("$.published[0].members[0].classSection").value("T1"))
        .andExpect(jsonPath("$.published[0].members[0].attendanceApplicable").value(true))
        .andExpect(jsonPath("$.published[0].members[0].blocked").value(false))
        .andExpect(jsonPath("$.published[0].members[1].name").value("Carla"))
        .andExpect(jsonPath("$.published[0].members[1].classSection").value("T2"))
        .andExpect(jsonPath("$.published[1].number").value(2))
        .andExpect(jsonPath("$.published[1].sharedLanguages.length()").value(0));
  }

  @Test
  @DisplayName("No groups yet is 200 with both lists empty, so the page offers Generate groups")
  void noGroups() throws Exception {
    viewGroups(project())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.draft.length()").value(0))
        .andExpect(jsonPath("$.published.length()").value(0));
  }

  @Test
  @DisplayName("Until a regeneration is confirmed, the drafts and the published groups both come back")
  void draftsNextToPublished() throws Exception {
    // Arrange - a draft group 1 next to the published group 1
    Assignment assignment = project();
    group(assignment, 1, true, ana, bruno, carla, duarte);
    group(assignment, 1, false, ana, bruno);
    group(assignment, 2, false, carla, duarte);

    // Act + Assert
    viewGroups(assignment)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.draft[*].number", Matchers.contains(1, 2)))
        .andExpect(jsonPath("$.draft[*].status", Matchers.everyItem(Matchers.is("DRAFT"))))
        .andExpect(jsonPath("$.published.length()").value(1))
        .andExpect(jsonPath("$.published[0].status").value("PUBLISHED"))
        .andExpect(jsonPath("$.published[0].size").value(4));
  }

  @Test
  @DisplayName("A student with a statute shows attendance not applicable, never the statute type (GR7)")
  void statuteShowsNotApplicable() throws Exception {
    // Arrange
    bruno.assignStatute(StatuteType.HIGH_PERFORMANCE_ATHLETE, LocalDate.now().plusYears(1));
    studentRepository.save(bruno);
    Assignment assignment = project();
    group(assignment, 1, true, ana, bruno);

    // Act
    String body = viewGroups(assignment)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.published[0].members[0].attendanceApplicable").value(true))
        .andExpect(jsonPath("$.published[0].members[1].attendanceApplicable").value(false))
        .andExpect(jsonPath("$.published[0].members[1].attendance").doesNotExist())
        .andReturn().getResponse().getContentAsString();

    // Assert
    assertFalse(body.contains(StatuteType.HIGH_PERFORMANCE_ATHLETE.name()));
    assertFalse(body.toLowerCase().contains("statute"));
  }

  @Test
  @DisplayName("A blocked student is still listed in the group they were in, marked as blocked (GR5)")
  void blockedMemberStillListed() throws Exception {
    // Arrange
    duarte.block("test");
    studentRepository.save(duarte);
    Assignment assignment = project();
    group(assignment, 1, true, carla, duarte);

    // Act + Assert
    viewGroups(assignment)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.published[0].members[1].name").value("Duarte"))
        .andExpect(jsonPath("$.published[0].members[1].blocked").value(true));
  }

  @Test
  @DisplayName("Generated drafts can be read back: the same groups and members as the preview")
  void generatedDraftsReadBack() throws Exception {
    // Arrange
    Assignment assignment = project();
    String preview = mockMvc.perform(post("/subject-editions/" + edition.getId() + "/assignments/"
        + assignment.getId() + "/groups").with(csrf()).session(loginAs(TEACHER)))
        .andExpect(status().isCreated())
        .andReturn().getResponse().getContentAsString();

    // Act
    String draft = viewGroups(assignment)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.published.length()").value(0))
        .andReturn().getResponse().getContentAsString();

    // Assert
    assertEquals(JsonPath.<List<Object>>read(preview, "$[*].members[*].number"),
        JsonPath.<List<Object>>read(draft, "$.draft[*].members[*].number"));
  }

  private long selectsFor(MockHttpSession session, Assignment assignment) throws Exception {
    entityManager.flush();
    entityManager.clear();
    Statistics statistics = entityManager.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
    statistics.clear();
    mockMvc.perform(get("/subject-editions/" + edition.getId() + "/assignments/" + assignment.getId() + "/groups")
        .session(session))
        .andExpect(status().isOk());
    return statistics.getPrepareStatementCount();
  }

  @Test
  @DisplayName("More groups and members don't mean more queries")
  void queriesDontGrowWithTheMembers() throws Exception {
    // Arrange - measured with one published group, then a draft group of the other two
    Assignment assignment = project();
    group(assignment, 1, true, ana, bruno);
    MockHttpSession session = loginAs(TEACHER);
    long before = selectsFor(session, assignment);
    group(assignment, 2, false, carla, duarte);

    // Act
    long after = selectsFor(session, assignment);

    // Assert
    assertEquals(before, after);
  }

  @Test
  @DisplayName("An exam is 400 ASSIGNMENT_NOT_GROUP_WORK")
  void examIsNotGroupWork() throws Exception {
    // Arrange
    Assignment exam = assignmentRepository.save(new Assignment(edition, "Exam", AssignmentType.EXAM,
        LocalDate.of(2094, 1, 20), new BigDecimal("0.60"), null));

    // Act + Assert
    viewGroups(exam)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("ASSIGNMENT_NOT_GROUP_WORK"));
  }

  @Test
  @DisplayName("An assignment that doesn't exist is 404")
  void unknownAssignmentIsNotFound() throws Exception {
    viewGroups(TEACHER, edition.getId(), -1L)
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
  }

  @Test
  @DisplayName("A teacher of another edition is 403")
  void otherTeacherIsDenied() throws Exception {
    viewGroups(OTHER_TEACHER, edition.getId(), project().getId())
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("Staff is 403, only teachers view an assignment's groups")
  void staffIsDenied() throws Exception {
    viewGroups(STAFF, edition.getId(), project().getId())
        .andExpect(status().isForbidden());
  }

}
