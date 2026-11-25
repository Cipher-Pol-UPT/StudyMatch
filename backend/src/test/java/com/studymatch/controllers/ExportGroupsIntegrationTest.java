package com.studymatch.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.studymatch.enums.AssignmentType;
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

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ExportGroupsIntegrationTest {

  private static final String PASSWORD = "Test1234";
  private static final String TEACHER = "it.exportgroups.teacher@studymatch.com";
  private static final String OTHER_TEACHER = "it.exportgroups.other@studymatch.com";
  private static final String STAFF = "it.exportgroups.staff@studymatch.com";

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

  private SubjectEdition edition;
  private Degree degree;
  private Student ana;
  private Student bruno;
  private Student carla;

  @BeforeEach
  void setUp() {
    Teacher teacher = teacherRepository.save(withoutPasswordChange(new Teacher("it.exportgroups.teacher@example.com",
        TEACHER, "Test Teacher", passwordEncoder.encode(PASSWORD))));
    teacherRepository.save(withoutPasswordChange(new Teacher("it.exportgroups.other@example.com", OTHER_TEACHER,
        "Other Teacher", passwordEncoder.encode(PASSWORD))));
    staffRepository.save(withoutPasswordChange(new Staff("it.exportgroups.staff@example.com", STAFF, "Test Staff",
        passwordEncoder.encode(PASSWORD))));

    degree = degreeRepository.save(new Degree("IT Export Groups Degree", 3));
    Category category = categoryRepository.save(new Category("IT Export Groups Category"));
    Subject subject = subjectRepository.save(new Subject("ITEXPGRP1", "IT Export Groups Subject", 6, category));
    AcademicYear year = academicYearRepository.save(new AcademicYear(2094));
    edition = new SubjectEdition(subject, year, Set.of(teacher));
    edition.addClassSection("T1");
    edition = subjectEditionRepository.save(edition);

    ana = enrol(student("Ana"));
    bruno = enrol(student("Bruno"));
    carla = enrol(student("Carla"));
  }

  // saved first, the institutional email comes from the number
  private Student student(String name) {
    Student student = studentRepository.save(new Student("it.exportgroups." + name.toLowerCase() + "@example.com",
        name, "hash", degree, 1));
    student.generateInstitutionalEmail("studymatch.com");
    return studentRepository.save(student);
  }

  private Student enrol(Student student) {
    enrolmentRepository.save(new Enrolment(student, edition.getClassSection("T1")));
    return student;
  }

  private static <T extends User> T withoutPasswordChange(T user) {
    user.setMustChangePassword(false);
    return user;
  }

  private Assignment project() {
    return assignmentRepository.save(new Assignment(edition, "Project", AssignmentType.PROJECT,
        LocalDate.of(2095, 1, 15), new BigDecimal("0.40"), new GroupingRule(2, 2, 3, false)));
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

  private ResultActions export(String email, Long assignmentId, String format) throws Exception {
    return mockMvc.perform(get("/subject-editions/" + edition.getId() + "/assignments/" + assignmentId
        + "/groups/export").param("format", format).session(loginAs(email)));
  }

  private ResultActions export(Assignment assignment, String format) throws Exception {
    return export(TEACHER, assignment.getId(), format);
  }

  private static String email(Student student) {
    return student.getNumber() + "@students.studymatch.com";
  }

  @Test
  @DisplayName("CSV: a groups.csv download with the header and one row per student, by group and then by name")
  void csv() throws Exception {
    // Arrange
    Assignment assignment = project();
    group(assignment, 1, true, bruno, ana);
    group(assignment, 2, true, carla);

    // Act
    MvcResult result = export(assignment, "CSV")
        .andExpect(status().isOk())
        .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "text/csv;charset=UTF-8"))
        .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"groups.csv\""))
        .andReturn();

    // Assert
    String csv = new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
    assertEquals(List.of("﻿groupNumber,studentNumber,name,institutionalEmail",
        "1," + ana.getNumber() + ",Ana," + email(ana),
        "1," + bruno.getNumber() + ",Bruno," + email(bruno),
        "2," + carla.getNumber() + ",Carla," + email(carla)), csv.lines().toList());
  }

  @Test
  @DisplayName("PDF: a groups.pdf download with every group and its students")
  void pdf() throws Exception {
    // Arrange
    Assignment assignment = project();
    group(assignment, 1, true, ana, bruno);
    group(assignment, 2, true, carla);

    // Act
    MvcResult result = export(assignment, "PDF")
        .andExpect(status().isOk())
        .andExpect(header().string(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PDF_VALUE))
        .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"groups.pdf\""))
        .andReturn();

    // Assert
    byte[] pdf = result.getResponse().getContentAsByteArray();
    assertTrue(pdf.length > 0);
    try (PDDocument document = Loader.loadPDF(pdf)) {
      String text = new PDFTextStripper().getText(document).replaceAll("\\s+", " ");
      assertTrue(text.contains("IT Export Groups Subject"));
      assertTrue(text.contains("Group 1"));
      assertTrue(text.contains(carla.getNumber() + " Carla " + email(carla)));
    }
  }

  @Test
  @DisplayName("A student with a statute is exported like any other, with nothing about the statute (GR7)")
  void neverTheStatute() throws Exception {
    // Arrange
    bruno.assignStatute(StatuteType.HIGH_PERFORMANCE_ATHLETE, LocalDate.now().plusYears(1));
    studentRepository.save(bruno);
    Assignment assignment = project();
    group(assignment, 1, true, ana, bruno);

    // Act
    String csv = export(assignment, "CSV")
        .andExpect(status().isOk())
        .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    byte[] pdf = export(assignment, "PDF")
        .andExpect(status().isOk())
        .andReturn().getResponse().getContentAsByteArray();

    // Assert
    assertTrue(csv.contains("1," + bruno.getNumber() + ",Bruno," + email(bruno) + "\r\n"));
    try (PDDocument document = Loader.loadPDF(pdf)) {
      for (String text : List.of(csv, new PDFTextStripper().getText(document))) {
        assertFalse(text.toLowerCase().contains("statute"));
        assertFalse(text.contains(StatuteType.HIGH_PERFORMANCE_ATHLETE.name()));
      }
    }
  }

  @Test
  @DisplayName("No groups is 409 NO_GROUPS_TO_EXPORT")
  void noGroups() throws Exception {
    export(project(), "CSV")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("NO_GROUPS_TO_EXPORT"));
  }

  @Test
  @DisplayName("Only drafts is also 409 NO_GROUPS_TO_EXPORT, they can still change")
  void onlyDrafts() throws Exception {
    // Arrange
    Assignment assignment = project();
    group(assignment, 1, false, ana, bruno, carla);

    // Act + Assert
    export(assignment, "PDF")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("NO_GROUPS_TO_EXPORT"));
  }

  @Test
  @DisplayName("An exam is 400 ASSIGNMENT_NOT_GROUP_WORK")
  void examIsNotGroupWork() throws Exception {
    // Arrange
    Assignment exam = assignmentRepository.save(new Assignment(edition, "Exam", AssignmentType.EXAM,
        LocalDate.of(2095, 1, 20), new BigDecimal("0.60"), null));

    // Act + Assert
    export(exam, "CSV")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("ASSIGNMENT_NOT_GROUP_WORK"));
  }

  @Test
  @DisplayName("An assignment that doesn't exist is 404")
  void unknownAssignmentIsNotFound() throws Exception {
    export(TEACHER, -1L, "CSV")
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
  }

  @Test
  @DisplayName("A format other than CSV or PDF is 400 with the format as the invalid field")
  void unknownFormat() throws Exception {
    export(project(), "XLSX")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.fieldErrors[0].field").value("format"));
  }

  @Test
  @DisplayName("A teacher of another edition is 403 ACCESS_DENIED, even with published groups")
  void otherTeacherIsDenied() throws Exception {
    // Arrange
    Assignment assignment = project();
    group(assignment, 1, true, ana, bruno, carla);

    // Act + Assert
    export(OTHER_TEACHER, assignment.getId(), "CSV")
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
  }

  @Test
  @DisplayName("Staff is 403, only teachers export an assignment's groups")
  void staffIsDenied() throws Exception {
    export(STAFF, project().getId(), "CSV")
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("Nobody logged in gets 401")
  void notLoggedIn() throws Exception {
    mockMvc.perform(get("/subject-editions/" + edition.getId() + "/assignments/" + project().getId()
        + "/groups/export").param("format", "CSV"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
  }

}
