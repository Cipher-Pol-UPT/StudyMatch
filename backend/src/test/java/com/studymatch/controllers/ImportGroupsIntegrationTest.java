package com.studymatch.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
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
import com.studymatch.models.GroupingRule;
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
import com.studymatch.repositories.StudentRepository;
import com.studymatch.repositories.SubjectEditionRepository;
import com.studymatch.repositories.SubjectRepository;
import com.studymatch.repositories.TeacherRepository;

import jakarta.persistence.EntityManager;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ImportGroupsIntegrationTest {

  private static final String PASSWORD = "Test1234";
  private static final String TEACHER = "it.importgroups.teacher@studymatch.com";
  private static final String OTHER_TEACHER = "it.importgroups.other@studymatch.com";

  @Autowired private MockMvc mockMvc;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private EntityManager entityManager;
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

  private SubjectEdition edition;
  private Degree degree;
  private Assignment project;
  private Student ana;
  private Student bruno;
  private Student carla;
  private Student dora;
  private Student eva;

  @BeforeEach
  void setUp() {
    Teacher teacher = teacherRepository.save(withoutPasswordChange(new Teacher("it.importgroups.teacher@example.com",
        TEACHER, "Test Teacher", passwordEncoder.encode(PASSWORD))));
    teacherRepository.save(withoutPasswordChange(new Teacher("it.importgroups.other@example.com", OTHER_TEACHER,
        "Other Teacher", passwordEncoder.encode(PASSWORD))));

    degree = degreeRepository.save(new Degree("IT Import Groups Degree", 3));
    Category category = categoryRepository.save(new Category("IT Import Groups Category"));
    Subject subject = subjectRepository.save(new Subject("ITIMPGRP1", "IT Import Groups Subject", 6, category));
    AcademicYear year = academicYearRepository.save(new AcademicYear(2093));
    edition = new SubjectEdition(subject, year, Set.of(teacher));
    edition.addClassSection("T1");
    edition = subjectEditionRepository.save(edition);
    project = assignmentRepository.save(new Assignment(edition, "Project", AssignmentType.PROJECT,
        LocalDate.of(2094, 1, 15), new BigDecimal("0.40"), new GroupingRule(2, 2, 3, false)));

    ana = enrol(student("Ana"));
    bruno = enrol(student("Bruno"));
    carla = enrol(student("Carla"));
    dora = enrol(student("Dora"));
    eva = enrol(student("Eva"));
  }

  private Student student(String name) {
    return studentRepository.save(new Student("it.importgroups." + name.toLowerCase() + "@example.com", name, "hash",
        degree, 1));
  }

  private Student enrol(Student student) {
    enrolmentRepository.save(new Enrolment(student, edition.getClassSection("T1")));
    return student;
  }

  private static <T extends User> T withoutPasswordChange(T user) {
    user.setMustChangePassword(false);
    return user;
  }

  private void publishedGroup(int number, Student... members) {
    Group group = new Group(project, number);
    for (Student member : members) {
      group.addMember(member);
    }
    group.publish();
    groupRepository.save(group);
  }

  private MockHttpSession loginAs(String email) throws Exception {
    MvcResult result = mockMvc.perform(post("/users/login").with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
        .andReturn();
    assertEquals(200, result.getResponse().getStatus());
    return (MockHttpSession) result.getRequest().getSession(false);
  }

  private String groupsUrl() {
    return "/subject-editions/" + edition.getId() + "/assignments/" + project.getId() + "/groups";
  }

  private ResultActions importFile(String email, byte[] csv, boolean replacePublished) throws Exception {
    MockMultipartFile file = new MockMultipartFile("file", "groups.csv", "text/csv", csv);
    return mockMvc.perform(multipart(groupsUrl() + "/import").file(file)
        .param("replacePublished", String.valueOf(replacePublished))
        .with(csrf()).session(loginAs(email)));
  }

  private ResultActions importCsv(String csv) throws Exception {
    return importFile(TEACHER, csv.getBytes(StandardCharsets.UTF_8), false);
  }

  // each group's member numbers, by group number
  private List<Set<Long>> members(GroupStatus status) {
    entityManager.flush();
    entityManager.clear();
    return groupRepository.findWithMembersByAssignmentAndStatusOrderByNumber(project, status).stream()
        .map(group -> group.getMembers().stream().map(Student::getNumber).collect(Collectors.toSet()))
        .toList();
  }

  @Test
  @DisplayName("Import, then publish: the file's groups and a placed leftover become the published groups, the "
      + "skipped rows are listed")
  void importThenPublish() throws Exception {
    // Arrange - Eva isn't in the file, someone from outside the edition is, and Frank is blocked
    Student outsider = student("Outsider");
    Student frank = enrol(student("Frank"));
    frank.block("test");
    studentRepository.save(frank);
    String csv = "groupNumber,studentNumber,name\n"
        + "1," + ana.getNumber() + ",Ana\n"
        + "1," + bruno.getNumber() + ",Bruno\n"
        + "2," + carla.getNumber() + ",Carla\n"
        + "2," + outsider.getNumber() + ",Outsider\n"
        + "2," + dora.getNumber() + ",Dora\n"
        + "3," + frank.getNumber() + ",Frank\n";

    // Act
    importCsv(csv)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.imported").value(4))
        .andExpect(jsonPath("$.leftover").value(1))
        .andExpect(jsonPath("$.placed[0]").value(eva.getNumber()))
        .andExpect(jsonPath("$.skipped[0].row").value(5))
        .andExpect(jsonPath("$.skipped[0].reason").value("NOT_ENROLLED"))
        .andExpect(jsonPath("$.skipped[1].row").value(7))
        .andExpect(jsonPath("$.skipped[1].reason").value("BLOCKED"))
        .andExpect(jsonPath("$.groups[0].origin").value("IMPORTED"))
        .andExpect(jsonPath("$.groups[0].group.status").value("DRAFT"))
        .andExpect(jsonPath("$.groups[0].group.size").value(3))
        .andExpect(jsonPath("$.groups[1].group.number").value(2));
    mockMvc.perform(post(groupsUrl() + "/publish").with(csrf()).session(loginAs(TEACHER)))
        .andExpect(status().isNoContent());

    // Assert - Eva went into the first of the two groups of 2
    assertEquals(List.of(Set.of(ana.getNumber(), bruno.getNumber(), eva.getNumber()),
        Set.of(carla.getNumber(), dora.getNumber())), members(GroupStatus.PUBLISHED));
    assertTrue(members(GroupStatus.DRAFT).isEmpty());
  }

  @Test
  @DisplayName("An exported file imports back as the same groups, and cancelling keeps the published ones")
  void exportedFileThenCancel() throws Exception {
    // Arrange
    publishedGroup(1, ana, bruno, eva);
    publishedGroup(2, carla, dora);
    byte[] exported = mockMvc.perform(get(groupsUrl() + "/export").param("format", "CSV").session(loginAs(TEACHER)))
        .andExpect(status().isOk())
        .andReturn().getResponse().getContentAsByteArray();

    // Act - first without confirming the replace, then with it
    importFile(TEACHER, exported, false)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("GROUPS_ALREADY_PUBLISHED"));
    importFile(TEACHER, exported, true)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.imported").value(5))
        .andExpect(jsonPath("$.leftover").value(0));
    List<Set<Long>> drafts = members(GroupStatus.DRAFT);
    mockMvc.perform(delete(groupsUrl() + "/draft").with(csrf()).session(loginAs(TEACHER)))
        .andExpect(status().isNoContent());

    // Assert
    List<Set<Long>> published = List.of(Set.of(ana.getNumber(), bruno.getNumber(), eva.getNumber()),
        Set.of(carla.getNumber(), dora.getNumber()));
    assertEquals(published, drafts);
    assertEquals(published, members(GroupStatus.PUBLISHED));
    assertTrue(members(GroupStatus.DRAFT).isEmpty());
  }

  @Test
  @DisplayName("Invalid rows are 400 GROUP_IMPORT_INVALID_ROWS listing each one, and nothing is saved")
  void invalidRows() throws Exception {
    // Arrange
    String csv = "groupNumber;studentNumber\n"
        + "1;" + ana.getNumber() + "\n"
        + "x;" + bruno.getNumber() + "\n"
        + "1;999999999\n"
        + "2;" + ana.getNumber() + "\n";

    // Act + Assert
    importCsv(csv)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("GROUP_IMPORT_INVALID_ROWS"))
        .andExpect(jsonPath("$.params.rows.length()").value(3))
        .andExpect(jsonPath("$.params.rows[0].row").value(3))
        .andExpect(jsonPath("$.params.rows[0].reason").value("INVALID"))
        .andExpect(jsonPath("$.params.rows[1].number").value(999999999))
        .andExpect(jsonPath("$.params.rows[1].reason").value("UNKNOWN_STUDENT"))
        .andExpect(jsonPath("$.params.rows[2].row").value(5))
        .andExpect(jsonPath("$.params.rows[2].reason").value("IN_TWO_GROUPS"));
    assertTrue(members(GroupStatus.DRAFT).isEmpty());
  }

  @Test
  @DisplayName("A file with nobody enrolled here is 400 GROUP_FILE_NOT_THIS_EDITION")
  void notThisEdition() throws Exception {
    // Arrange
    Student outsider = student("Outsider");

    // Act + Assert
    importCsv("groupNumber,studentNumber\n1," + outsider.getNumber() + "\n")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("GROUP_FILE_NOT_THIS_EDITION"));
  }

  @Test
  @DisplayName("A file without groupNumber is 400 CSV_MISSING_COLUMNS naming it")
  void missingColumn() throws Exception {
    importCsv("studentNumber,name\n" + ana.getNumber() + ",Ana\n")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("CSV_MISSING_COLUMNS"))
        .andExpect(jsonPath("$.params.columns").value("groupNumber"));
  }

  @Test
  @DisplayName("A teacher of another edition is 403 ACCESS_DENIED")
  void otherTeacherIsDenied() throws Exception {
    importFile(OTHER_TEACHER, ("groupNumber,studentNumber\n1," + ana.getNumber() + "\n")
        .getBytes(StandardCharsets.UTF_8), false)
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
  }

  @Test
  @DisplayName("The template is a groups.csv with the export's columns")
  void template() throws Exception {
    MvcResult result = mockMvc.perform(get("/subject-editions/groups/import/template").session(loginAs(TEACHER)))
        .andExpect(status().isOk())
        .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"groups.csv\""))
        .andReturn();
    assertEquals("\uFEFFgroupNumber,studentNumber,name,institutionalEmail\r\n",
        result.getResponse().getContentAsString(StandardCharsets.UTF_8));
  }

}
