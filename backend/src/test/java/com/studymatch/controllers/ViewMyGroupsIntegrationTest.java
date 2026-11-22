package com.studymatch.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

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

import com.studymatch.enums.AssignmentType;
import com.studymatch.models.AcademicYear;
import com.studymatch.models.Assignment;
import com.studymatch.models.Category;
import com.studymatch.models.Degree;
import com.studymatch.models.Group;
import com.studymatch.models.GroupingRule;
import com.studymatch.models.PersonalityProfile;
import com.studymatch.models.Student;
import com.studymatch.models.Subject;
import com.studymatch.models.SubjectEdition;
import com.studymatch.models.Teacher;
import com.studymatch.repositories.AcademicYearRepository;
import com.studymatch.repositories.AssignmentRepository;
import com.studymatch.repositories.CategoryRepository;
import com.studymatch.repositories.DegreeRepository;
import com.studymatch.repositories.GroupRepository;
import com.studymatch.repositories.StudentRepository;
import com.studymatch.repositories.SubjectEditionRepository;
import com.studymatch.repositories.SubjectRepository;
import com.studymatch.repositories.TeacherRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ViewMyGroupsIntegrationTest {

  private static final String PASSWORD = "Test1234";
  private static final String TEACHER = "it.mygroups.teacher@studymatch.com";

  @Autowired private MockMvc mockMvc;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private TeacherRepository teacherRepository;
  @Autowired private DegreeRepository degreeRepository;
  @Autowired private CategoryRepository categoryRepository;
  @Autowired private SubjectRepository subjectRepository;
  @Autowired private AcademicYearRepository academicYearRepository;
  @Autowired private SubjectEditionRepository subjectEditionRepository;
  @Autowired private StudentRepository studentRepository;
  @Autowired private AssignmentRepository assignmentRepository;
  @Autowired private GroupRepository groupRepository;

  private Degree degree;
  private SubjectEdition edition;
  private Student ana;
  private Student rui;
  private Student eva;

  // one edition with a teacher and three students, nobody in a group yet
  @BeforeEach
  void setUp() {
    Teacher teacher = new Teacher("it.mygroups.teacher@example.com", TEACHER, "Test Teacher",
        passwordEncoder.encode(PASSWORD));
    teacher.setMustChangePassword(false);
    teacher = teacherRepository.save(teacher);

    degree = degreeRepository.save(new Degree("IT My Groups Degree", 3));
    Category category = categoryRepository.save(new Category("IT My Groups Category"));
    Subject subject = subjectRepository.save(new Subject("ITMYGROUPS", "IT My Groups Subject", 6, category));
    AcademicYear year = academicYearRepository.save(new AcademicYear(2093));
    edition = new SubjectEdition(subject, year, Set.of(teacher));
    edition.addClassSection("T1");
    edition = subjectEditionRepository.save(edition);

    ana = saveStudent("it.mygroups.ana@example.com", "Ana My Groups");
    rui = saveStudent("it.mygroups.rui@example.com", "Rui My Groups");
    eva = saveStudent("it.mygroups.eva@example.com", "Eva My Groups");
  }

  private Student saveStudent(String personalEmail, String name) {
    Student student = new Student(personalEmail, name, passwordEncoder.encode(PASSWORD), degree, 1);
    student.setMustChangePassword(false);
    student.setPersonalityProfile(new PersonalityProfile(0.5, 0.5, 0.5, 0.5, 0.5));
    student = studentRepository.save(student);
    student.generateInstitutionalEmail("studymatch.com");
    return studentRepository.save(student);
  }

  private Assignment project(String name, LocalDate date) {
    return assignmentRepository.save(new Assignment(edition, name, AssignmentType.PROJECT, date,
        new BigDecimal("0.20"), new GroupingRule(2, 2, 3, false)));
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

  @Test
  @DisplayName("A student sees their published group with the subject, assignment, date and every member's email")
  void studentSeesPublishedGroup() throws Exception {
    // Arrange
    Assignment assignment = project("IT Project", LocalDate.of(2094, 1, 15));
    Group group = group(assignment, 2, true, ana, rui);

    // Act + Assert
    mockMvc.perform(get("/students/me/groups").session(loginAs(ana.getInstitutionalEmail())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].id").value(group.getId()))
        .andExpect(jsonPath("$[0].number").value(2))
        .andExpect(jsonPath("$[0].subjectCode").value("ITMYGROUPS"))
        .andExpect(jsonPath("$[0].subjectName").value("IT My Groups Subject"))
        .andExpect(jsonPath("$[0].assignmentId").value(assignment.getId()))
        .andExpect(jsonPath("$[0].assignmentName").value("IT Project"))
        .andExpect(jsonPath("$[0].assignmentDate").value("2094-01-15"))
        .andExpect(jsonPath("$[0].members.length()").value(2))
        .andExpect(jsonPath("$[0].members[0].number").value(ana.getNumber()))
        .andExpect(jsonPath("$[0].members[0].name").value("Ana My Groups"))
        .andExpect(jsonPath("$[0].members[0].institutionalEmail").value(ana.getInstitutionalEmail()))
        .andExpect(jsonPath("$[0].members[0].blocked").value(false))
        .andExpect(jsonPath("$[0].members[1].institutionalEmail").value(rui.getInstitutionalEmail()));
  }

  @Test
  @DisplayName("Drafts and other students' groups aren't shown, and the latest assignment comes first")
  void onlyOwnPublishedGroupsLatestFirst() throws Exception {
    // Arrange
    Assignment earlier = project("IT Earlier", LocalDate.of(2094, 1, 10));
    Assignment later = project("IT Later", LocalDate.of(2094, 3, 10));
    group(earlier, 1, true, ana, rui);
    group(later, 1, true, ana, eva);
    // a regenerated draft of the later assignment, and a group ana isn't in
    group(later, 1, false, ana, rui);
    group(earlier, 2, true, rui, eva);

    // Act + Assert
    mockMvc.perform(get("/students/me/groups").session(loginAs(ana.getInstitutionalEmail())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[0].assignmentName").value("IT Later"))
        .andExpect(jsonPath("$[0].members[1].number").value(eva.getNumber()))
        .andExpect(jsonPath("$[1].assignmentName").value("IT Earlier"))
        .andExpect(jsonPath("$[1].number").value(1));
  }

  @Test
  @DisplayName("A blocked member still shows in the group, marked as blocked (GR5)")
  void blockedMemberStillShows() throws Exception {
    // Arrange
    group(project("IT Project", LocalDate.of(2094, 1, 15)), 1, true, ana, rui);
    rui.block("test");
    studentRepository.save(rui);

    // Act + Assert
    mockMvc.perform(get("/students/me/groups").session(loginAs(ana.getInstitutionalEmail())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].members.length()").value(2))
        .andExpect(jsonPath("$[0].members[1].number").value(rui.getNumber()))
        .andExpect(jsonPath("$[0].members[1].blocked").value(true));
  }

  @Test
  @DisplayName("A student without published groups gets an empty list")
  void noGroups() throws Exception {
    // Arrange - only a draft so far
    group(project("IT Project", LocalDate.of(2094, 1, 15)), 1, false, ana, rui);

    // Act + Assert
    mockMvc.perform(get("/students/me/groups").session(loginAs(ana.getInstitutionalEmail())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
  }

  @Test
  @DisplayName("A teacher gets 403")
  void teacherForbidden() throws Exception {
    // Act + Assert
    mockMvc.perform(get("/students/me/groups").session(loginAs(TEACHER)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
  }

  @Test
  @DisplayName("Nobody logged in gets 401")
  void notLoggedIn() throws Exception {
    // Act + Assert
    mockMvc.perform(get("/students/me/groups"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
  }

}
