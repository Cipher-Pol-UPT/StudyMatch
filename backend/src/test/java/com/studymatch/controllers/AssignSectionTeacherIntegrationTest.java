package com.studymatch.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.studymatch.models.AcademicYear;
import com.studymatch.models.Category;
import com.studymatch.models.Staff;
import com.studymatch.models.Subject;
import com.studymatch.models.SubjectEdition;
import com.studymatch.models.Teacher;
import com.studymatch.repositories.AcademicYearRepository;
import com.studymatch.repositories.CategoryRepository;
import com.studymatch.repositories.StaffRepository;
import com.studymatch.repositories.SubjectEditionRepository;
import com.studymatch.repositories.SubjectRepository;
import com.studymatch.repositories.TeacherRepository;

import jakarta.persistence.EntityManager;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AssignSectionTeacherIntegrationTest {

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
  private AcademicYearRepository academicYearRepository;

  @Autowired
  private SubjectRepository subjectRepository;

  @Autowired
  private SubjectEditionRepository subjectEditionRepository;

  @Autowired
  private PasswordEncoder passwordEncoder;

  private Subject subject;
  private Teacher ana;
  private Teacher rui;
  private Teacher eva;
  private SubjectEdition pastEdition;
  private SubjectEdition currentEdition;
  private MockHttpSession staff;

  // ana and rui teach this year's edition with T1 and T2, eva teaches nothing
  @BeforeEach
  void setUp() throws Exception {
    Category category = categoryRepository.save(new Category("IT Section Teacher Category"));
    AcademicYear pastYear = academicYearRepository.save(new AcademicYear(PAST_YEAR));
    AcademicYear currentYear = academicYearRepository.save(new AcademicYear(CURRENT_YEAR));
    ana = teacherRepository.save(teacher("ana", "IT Section Ana"));
    rui = teacherRepository.save(teacher("rui", "IT Section Rui"));
    eva = teacherRepository.save(teacher("eva", "IT Section Eva"));

    subject = subjectRepository.save(new Subject("ITSEC1", "IT Section Subject", 6, category));
    SubjectEdition past = new SubjectEdition(subject, pastYear, Set.of(ana));
    past.addClassSection("T1");
    pastEdition = subjectEditionRepository.save(past);
    SubjectEdition current = new SubjectEdition(subject, currentYear, Set.of(ana, rui));
    current.addClassSection("T1");
    current.addClassSection("T2");
    currentEdition = subjectEditionRepository.save(current);

    Staff member = new Staff("it.section.staff@example.com", "it.section.staff@studymatch.com", "Test Staff",
        passwordEncoder.encode(PASSWORD));
    member.setMustChangePassword(false);
    staffRepository.save(member);
    staff = loginAs("it.section.staff@studymatch.com");
  }

  private Teacher teacher(String login, String name) {
    Teacher teacher = new Teacher("it.section." + login + "@example.com", "it.section." + login + "@studymatch.com",
        name, passwordEncoder.encode(PASSWORD));
    teacher.setMustChangePassword(false);
    return teacher;
  }

  private MockHttpSession loginAs(String email) throws Exception {
    MvcResult result = mockMvc.perform(post("/users/login").with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
        .andReturn();
    assertEquals(200, result.getResponse().getStatus());
    return (MockHttpSession) result.getRequest().getSession(false);
  }

  private ResultActions assign(MockHttpSession session, long editionId, String classSection, String body)
      throws Exception {
    return mockMvc.perform(put("/subject-editions/" + editionId + "/class-sections/" + classSection + "/teacher")
        .with(csrf()).session(session)
        .contentType(MediaType.APPLICATION_JSON)
        .content(body));
  }

  private static String teacherNumber(Teacher teacher) {
    return "{\"teacherNumber\":" + teacher.getNumber() + "}";
  }

  // read what the request saved, not the copies this test already holds
  private Teacher teacherInDatabase(SubjectEdition edition, String classSection) {
    entityManager.flush();
    entityManager.clear();
    return subjectEditionRepository.findById(edition.getId()).orElseThrow().getClassSection(classSection).getTeacher();
  }

  @Test
  @DisplayName("Staff picks a class's teacher: 200 with every class sorted, saved, and shown in the current edition")
  void staffAssignsTeacher() throws Exception {
    // Act
    assign(staff, currentEdition.getId(), "T2", teacherNumber(rui))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(currentEdition.getId()))
        .andExpect(jsonPath("$.classSections[0]").value("T1"))
        .andExpect(jsonPath("$.sectionTeachers.length()").value(2))
        .andExpect(jsonPath("$.sectionTeachers[0].classSection").value("T1"))
        .andExpect(jsonPath("$.sectionTeachers[0].teacher").doesNotExist())
        .andExpect(jsonPath("$.sectionTeachers[1].classSection").value("T2"))
        .andExpect(jsonPath("$.sectionTeachers[1].teacher.number").value(rui.getNumber()))
        .andExpect(jsonPath("$.sectionTeachers[1].teacher.name").value("IT Section Rui"));

    // Assert
    assertEquals(rui.getNumber(), teacherInDatabase(currentEdition, "T2").getNumber());
    mockMvc.perform(get("/subjects/" + subject.getId() + "/editions/current").session(staff))
        .andExpect(jsonPath("$.sectionTeachers[1].teacher.number").value(rui.getNumber()));
  }

  @Test
  @DisplayName("Picking another of the edition's teachers replaces the class's teacher")
  void staffReplacesTeacher() throws Exception {
    // Arrange
    assign(staff, currentEdition.getId(), "T1", teacherNumber(ana)).andExpect(status().isOk());

    // Act
    assign(staff, currentEdition.getId(), "T1", teacherNumber(rui))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.sectionTeachers[0].teacher.number").value(rui.getNumber()));

    // Assert
    assertEquals(rui.getNumber(), teacherInDatabase(currentEdition, "T1").getNumber());
  }

  @Test
  @DisplayName("A teacher who isn't one of the edition's is 400 TEACHER_NOT_IN_SUBJECT_EDITION and the class keeps none")
  void outsideTeacherIsRefused() throws Exception {
    // Act
    assign(staff, currentEdition.getId(), "T1", teacherNumber(eva))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("TEACHER_NOT_IN_SUBJECT_EDITION"));

    // Assert
    assertNull(teacherInDatabase(currentEdition, "T1"));
  }

  @Test
  @DisplayName("A class the edition doesn't have is 400 CLASS_SECTION_NOT_IN_SUBJECT_EDITION")
  void unknownClassIsRefused() throws Exception {
    assign(staff, currentEdition.getId(), "T9", teacherNumber(ana))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("CLASS_SECTION_NOT_IN_SUBJECT_EDITION"));
  }

  @Test
  @DisplayName("A teacher number that doesn't exist is 404 NOT_FOUND")
  void unknownTeacherIsNotFound() throws Exception {
    assign(staff, currentEdition.getId(), "T1", "{\"teacherNumber\":999999999}")
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
  }

  @Test
  @DisplayName("An edition that doesn't exist is 404 NOT_FOUND")
  void unknownEditionIsNotFound() throws Exception {
    assign(staff, 999999999L, "T1", teacherNumber(ana))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
  }

  @Test
  @DisplayName("An older year's edition is 409 SUBJECT_EDITION_NOT_CURRENT and its class keeps none")
  void olderEditionIsRefused() throws Exception {
    // Act
    assign(staff, pastEdition.getId(), "T1", teacherNumber(ana))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("SUBJECT_EDITION_NOT_CURRENT"));

    // Assert
    assertNull(teacherInDatabase(pastEdition, "T1"));
  }

  @Test
  @DisplayName("Without a teacher number it's 400 VALIDATION_FAILED on teacherNumber")
  void missingTeacherNumberIsInvalid() throws Exception {
    assign(staff, currentEdition.getId(), "T1", "{}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.fieldErrors[0].field").value("teacherNumber"));
  }

  @Test
  @DisplayName("A teacher can't pick a class's teacher, not even their own edition's (403 ACCESS_DENIED)")
  void teacherIsDenied() throws Exception {
    // Arrange
    MockHttpSession session = loginAs("it.section.ana@studymatch.com");

    // Act
    assign(session, currentEdition.getId(), "T1", teacherNumber(ana))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

    // Assert
    assertNull(teacherInDatabase(currentEdition, "T1"));
  }

  @Test
  @DisplayName("Without logging in it's 401")
  void notLoggedIn() throws Exception {
    mockMvc.perform(put("/subject-editions/" + currentEdition.getId() + "/class-sections/T1/teacher").with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content(teacherNumber(ana)))
        .andExpect(status().isUnauthorized());
  }

}
