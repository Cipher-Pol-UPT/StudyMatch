package com.studymatch.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Set;
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

import com.studymatch.models.AcademicYear;
import com.studymatch.models.Category;
import com.studymatch.models.Degree;
import com.studymatch.models.Staff;
import com.studymatch.models.Subject;
import com.studymatch.models.SubjectEdition;
import com.studymatch.models.Teacher;
import com.studymatch.repositories.AcademicYearRepository;
import com.studymatch.repositories.CategoryRepository;
import com.studymatch.repositories.DegreeRepository;
import com.studymatch.repositories.StaffRepository;
import com.studymatch.repositories.SubjectEditionRepository;
import com.studymatch.repositories.SubjectRepository;
import com.studymatch.repositories.TeacherRepository;

import jakarta.persistence.EntityManager;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class UpdateSubjectIntegrationTest {

  private static final String PASSWORD = "Test1234";
  // far in the future so it can't clash with real academic years; the newest one is the current year
  private static final int PAST_YEAR = 2093;
  private static final int CURRENT_YEAR = 2094;

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private EntityManager entityManager;

  @Autowired
  private StaffRepository staffRepository;

  @Autowired
  private TeacherRepository teacherRepository;

  @Autowired
  private CategoryRepository categoryRepository;

  @Autowired
  private DegreeRepository degreeRepository;

  @Autowired
  private AcademicYearRepository academicYearRepository;

  @Autowired
  private SubjectRepository subjectRepository;

  @Autowired
  private SubjectEditionRepository subjectEditionRepository;

  @Autowired
  private PasswordEncoder passwordEncoder;

  private Category category;
  private Subject subject;
  private Teacher ana;
  private Teacher rui;
  private SubjectEdition pastEdition;
  private SubjectEdition currentEdition;

  @BeforeEach
  void saveSubjectWithEditions() {
    category = categoryRepository.save(new Category("IT Update Subject Category"));
    Degree degree = degreeRepository.save(new Degree("IT Update Subject Degree", 3));
    AcademicYear pastYear = academicYearRepository.save(new AcademicYear(PAST_YEAR));
    AcademicYear currentYear = academicYearRepository.save(new AcademicYear(CURRENT_YEAR));
    ana = teacherRepository.save(teacher("ana", "IT Update Ana"));
    rui = teacherRepository.save(teacher("rui", "IT Update Rui"));

    Subject toSave = new Subject("ITUPD1", "IT Update Subject", 6, category);
    toSave.addDegree(degree, 1, 1);
    subject = subjectRepository.save(toSave);
    pastEdition = subjectEditionRepository.save(new SubjectEdition(subject, pastYear, Set.of(ana)));
    SubjectEdition current = new SubjectEdition(subject, currentYear, Set.of(ana));
    current.addClassSection("T1");
    currentEdition = subjectEditionRepository.save(current);
  }

  private Teacher teacher(String login, String name) {
    Teacher teacher = new Teacher("it.update." + login + "@example.com", "it.update." + login + "@studymatch.com", name,
        passwordEncoder.encode(PASSWORD));
    teacher.setMustChangePassword(false);
    return teacher;
  }

  private String update(String name, int ects, String teacherNumbers) {
    return "{\"name\":\"" + name + "\",\"ects\":" + ects
        + (teacherNumbers == null ? "" : ",\"teacherNumbers\":" + teacherNumbers) + "}";
  }

  private MockHttpSession loginAsStaff() throws Exception {
    Staff staff = new Staff("it.update.staff@example.com", "it.update.staff@studymatch.com", "Test Staff",
        passwordEncoder.encode(PASSWORD));
    staff.setMustChangePassword(false);
    staffRepository.save(staff);
    return loginAs("it.update.staff@studymatch.com", PASSWORD);
  }

  private MockHttpSession loginAs(String email, String password) throws Exception {
    MvcResult result = mockMvc.perform(post("/users/login").with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
        .andReturn();
    assertEquals(200, result.getResponse().getStatus());
    return (MockHttpSession) result.getRequest().getSession(false);
  }

  private ResultActions updateSubject(MockHttpSession session, long subjectId, String body) throws Exception {
    return mockMvc.perform(put("/subjects/" + subjectId).with(csrf()).session(session)
        .contentType(MediaType.APPLICATION_JSON)
        .content(body));
  }

  private ResultActions getCurrentEdition(MockHttpSession session, long subjectId) throws Exception {
    return mockMvc.perform(get("/subjects/" + subjectId + "/editions/current").session(session));
  }

  // read what was committed by the request, not the copies this test already holds
  private Set<String> teacherNames(SubjectEdition edition) {
    entityManager.flush();
    entityManager.clear();
    return subjectEditionRepository.findById(edition.getId()).orElseThrow().getTeachers().stream()
        .map(Teacher::getName)
        .collect(Collectors.toSet());
  }

  private Subject reloaded() {
    entityManager.flush();
    entityManager.clear();
    return subjectRepository.findById(subject.getId()).orElseThrow();
  }

  // the staff reads the subject's current edition and its teachers

  @Test
  @DisplayName("Staff gets the subject's edition this year, with its teachers and classes")
  void staffGetsCurrentEdition() throws Exception {
    // Arrange
    MockHttpSession staff = loginAsStaff();

    // Act + Assert
    getCurrentEdition(staff, subject.getId())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(currentEdition.getId()))
        .andExpect(jsonPath("$.academicYear").value(CURRENT_YEAR))
        .andExpect(jsonPath("$.subject.code").value("ITUPD1"))
        .andExpect(jsonPath("$.teachers.length()").value(1))
        .andExpect(jsonPath("$.teachers[0].number").value(ana.getNumber()))
        .andExpect(jsonPath("$.classSections[0]").value("T1"));
  }

  @Test
  @DisplayName("A subject without an edition this year has no current edition (404 NOT_FOUND)")
  void noCurrentEditionIsNotFound() throws Exception {
    // Arrange
    MockHttpSession staff = loginAsStaff();
    Subject other = subjectRepository.save(new Subject("ITUPD2", "IT Update Other", 6, category));
    subjectEditionRepository.save(new SubjectEdition(other, pastEdition.getAcademicYear(), Set.of(ana)));

    // Act + Assert
    getCurrentEdition(staff, other.getId())
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
  }

  // the staff updates the subject

  @Test
  @DisplayName("Staff changes name, ECTS and this year's teachers: 200, code and category stay, past editions keep theirs")
  void staffUpdatesSubject() throws Exception {
    // Arrange
    MockHttpSession staff = loginAsStaff();

    // Act
    updateSubject(staff, subject.getId(), update("  IT Updated Name ", 7, "[" + rui.getNumber() + "]"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(subject.getId()))
        .andExpect(jsonPath("$.code").value("ITUPD1"))
        .andExpect(jsonPath("$.name").value("IT Updated Name"))
        .andExpect(jsonPath("$.ects").value(7))
        .andExpect(jsonPath("$.category.id").value(category.getId()))
        .andExpect(jsonPath("$.degrees.length()").value(1));

    // Assert
    Subject saved = reloaded();
    assertEquals("IT Updated Name", saved.getName());
    assertEquals(7, saved.getEcts());
    assertEquals(Set.of("IT Update Rui"), teacherNames(currentEdition));
    assertEquals(Set.of("IT Update Ana"), teacherNames(pastEdition));
  }

  @Test
  @DisplayName("Staff can add a teacher next to the ones already there")
  void staffAddsTeacher() throws Exception {
    // Arrange
    MockHttpSession staff = loginAsStaff();

    // Act
    updateSubject(staff, subject.getId(), update("IT Update Subject", 6, "[" + ana.getNumber() + "," + rui.getNumber() + "]"))
        .andExpect(status().isOk());

    // Assert
    assertEquals(Set.of("IT Update Ana", "IT Update Rui"), teacherNames(currentEdition));
  }

  @Test
  @DisplayName("Without teachers in the request only name and ECTS change, even with no edition this year")
  void updateWithoutTeachers() throws Exception {
    // Arrange
    MockHttpSession staff = loginAsStaff();
    Subject other = subjectRepository.save(new Subject("ITUPD2", "IT Update Other", 6, category));

    // Act
    updateSubject(staff, other.getId(), update("IT Other Renamed", 5, null))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("IT Other Renamed"));

    // Assert
    entityManager.flush();
    entityManager.clear();
    assertEquals("IT Other Renamed", subjectRepository.findById(other.getId()).orElseThrow().getName());
  }

  @Test
  @DisplayName("A code or category in the body is ignored: they can't change")
  void codeAndCategoryCantChange() throws Exception {
    // Arrange
    MockHttpSession staff = loginAsStaff();
    Category other = categoryRepository.save(new Category("IT Update Other Category"));

    // Act
    updateSubject(staff, subject.getId(), "{\"code\":\"HACKED\",\"categoryId\":" + other.getId()
        + ",\"name\":\"IT Update Subject\",\"ects\":6}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value("ITUPD1"))
        .andExpect(jsonPath("$.category.id").value(category.getId()));

    // Assert
    Subject saved = reloaded();
    assertEquals("ITUPD1", saved.getCode());
    assertEquals(category.getId(), saved.getCategory().getId());
  }

  // what can go wrong

  @Test
  @DisplayName("Removing every teacher is 400 AT_LEAST_ONE_TEACHER_REQUIRED and nothing changes")
  void removingEveryTeacherIsRefused() throws Exception {
    // Arrange
    MockHttpSession staff = loginAsStaff();

    // Act
    updateSubject(staff, subject.getId(), update("IT Updated Name", 7, "[]"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("AT_LEAST_ONE_TEACHER_REQUIRED"));

    // Assert
    assertEquals(Set.of("IT Update Ana"), teacherNames(currentEdition));
  }

  @Test
  @DisplayName("Removing a teacher who still teaches a class is 409 TEACHER_STILL_TEACHES_SECTION naming it, and nothing changes")
  void removingTeacherOfClassIsRefused() throws Exception {
    // Arrange
    MockHttpSession staff = loginAsStaff();
    currentEdition.assignTeacher("T1", ana);

    // Act
    updateSubject(staff, subject.getId(), update("IT Updated Name", 7, "[" + rui.getNumber() + "]"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("TEACHER_STILL_TEACHES_SECTION"))
        .andExpect(jsonPath("$.params.classSections").value("T1"));

    // Assert
    assertEquals(Set.of("IT Update Ana"), teacherNames(currentEdition));
  }

  @Test
  @DisplayName("Changing teachers of a subject without an edition this year is 404 NOT_FOUND")
  void teachersWithoutCurrentEdition() throws Exception {
    // Arrange
    MockHttpSession staff = loginAsStaff();
    Subject other = subjectRepository.save(new Subject("ITUPD2", "IT Update Other", 6, category));

    // Act + Assert
    updateSubject(staff, other.getId(), update("IT Other Renamed", 6, "[" + ana.getNumber() + "]"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
  }

  @Test
  @DisplayName("A teacher number that doesn't exist is 404 NOT_FOUND")
  void unknownTeacher() throws Exception {
    // Arrange
    MockHttpSession staff = loginAsStaff();

    // Act + Assert
    updateSubject(staff, subject.getId(), update("IT Update Subject", 6, "[" + ana.getNumber() + ",999999999]"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    assertEquals(Set.of("IT Update Ana"), teacherNames(currentEdition));
  }

  @Test
  @DisplayName("A subject that doesn't exist is 404 NOT_FOUND")
  void unknownSubject() throws Exception {
    // Arrange
    MockHttpSession staff = loginAsStaff();

    // Act + Assert
    updateSubject(staff, 999999999L, update("IT Update Subject", 6, null))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
  }

  @Test
  @DisplayName("A blank name and ECTS out of range are 400 VALIDATION_FAILED with both fields")
  void invalidFields() throws Exception {
    // Arrange
    MockHttpSession staff = loginAsStaff();

    // Act
    updateSubject(staff, subject.getId(), update(" ", 61, null))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.fieldErrors.length()").value(2));

    // Assert
    assertEquals("IT Update Subject", reloaded().getName());
  }

  // who isn't allowed

  @Test
  @DisplayName("A teacher can't update subjects or read their current edition (403 ACCESS_DENIED)")
  void teacherIsDenied() throws Exception {
    // Arrange
    MockHttpSession session = loginAs("it.update.ana@studymatch.com", PASSWORD);

    // Act + Assert
    updateSubject(session, subject.getId(), update("IT Updated Name", 6, null))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    getCurrentEdition(session, subject.getId())
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    assertEquals("IT Update Subject", reloaded().getName());
  }

  @Test
  @DisplayName("Without logging in it's 401")
  void notLoggedIn() throws Exception {
    mockMvc.perform(put("/subjects/" + subject.getId()).with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content(update("IT Updated Name", 6, null)))
        .andExpect(status().isUnauthorized());
    mockMvc.perform(get("/subjects/" + subject.getId() + "/editions/current"))
        .andExpect(status().isUnauthorized());
  }

}
