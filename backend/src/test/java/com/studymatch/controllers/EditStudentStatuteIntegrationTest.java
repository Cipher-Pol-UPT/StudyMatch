package com.studymatch.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import org.hamcrest.Matchers;
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

import com.studymatch.enums.StatuteType;
import com.studymatch.models.Degree;
import com.studymatch.models.Staff;
import com.studymatch.models.Student;
import com.studymatch.models.Teacher;
import com.studymatch.repositories.DegreeRepository;
import com.studymatch.repositories.StaffRepository;
import com.studymatch.repositories.StudentRepository;
import com.studymatch.repositories.TeacherRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class EditStudentStatuteIntegrationTest {

  private static final String PASSWORD = "Test1234";
  private static final String STAFF = "it.statute.staff@studymatch.com";
  private static final String TEACHER = "it.statute.teacher@studymatch.com";

  @Autowired private MockMvc mockMvc;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private StaffRepository staffRepository;
  @Autowired private TeacherRepository teacherRepository;
  @Autowired private DegreeRepository degreeRepository;
  @Autowired private StudentRepository studentRepository;

  private MockHttpSession staff;
  private Student ana;

  @BeforeEach
  void setUp() throws Exception {
    Degree degree = degreeRepository.save(new Degree("IT Statute Degree", 3));
    ana = studentRepository.save(new Student("it.statute.ana@example.com", "Ana Statute", "hash", degree, 1));

    Staff member = new Staff("it.statute.staff@example.com", STAFF, "Test Staff", passwordEncoder.encode(PASSWORD));
    member.setMustChangePassword(false);
    staffRepository.save(member);
    Teacher teacher = new Teacher("it.statute.teacher@example.com", TEACHER, "Test Teacher",
        passwordEncoder.encode(PASSWORD));
    teacher.setMustChangePassword(false);
    teacherRepository.save(teacher);
    staff = loginAs(STAFF);
  }

  private MockHttpSession loginAs(String email) throws Exception {
    MvcResult result = mockMvc.perform(post("/users/login").with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
        .andReturn();
    assertEquals(200, result.getResponse().getStatus());
    return (MockHttpSession) result.getRequest().getSession(false);
  }

  private static String statute(String type, LocalDate endDate) {
    return "{\"type\":\"" + type + "\",\"endDate\":\"" + endDate + "\"}";
  }

  private ResultActions setStatute(MockHttpSession session, Long number, String body) throws Exception {
    return mockMvc.perform(put("/students/" + number + "/statute").with(csrf()).session(session)
        .contentType(MediaType.APPLICATION_JSON)
        .content(body));
  }

  private ResultActions removeStatute(MockHttpSession session, Long number) throws Exception {
    return mockMvc.perform(delete("/students/" + number + "/statute").with(csrf()).session(session));
  }

  private ResultActions record() throws Exception {
    return mockMvc.perform(get("/students/" + ana.getNumber()).session(staff));
  }

  // adding and replacing

  @Test
  @DisplayName("Staff adds a statute and the student's record shows its type and end date")
  void staffAddsStatute() throws Exception {
    // Arrange
    LocalDate endDate = LocalDate.now().plusMonths(6);

    // Act
    setStatute(staff, ana.getNumber(), statute("WORKING_STUDENT", endDate))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.statuteType").value("WORKING_STUDENT"))
        .andExpect(jsonPath("$.statuteEndDate").value(endDate.toString()));

    // Assert
    record()
        .andExpect(jsonPath("$.statuteType").value("WORKING_STUDENT"))
        .andExpect(jsonPath("$.statuteEndDate").value(endDate.toString()));
  }

  @Test
  @DisplayName("Adding a statute of the same type replaces the current one")
  void sameTypeReplacesTheCurrentOne() throws Exception {
    // Arrange
    setStatute(staff, ana.getNumber(), statute("WORKING_STUDENT", LocalDate.now().plusMonths(1)))
        .andExpect(status().isOk());
    LocalDate newEndDate = LocalDate.now().plusYears(1);

    // Act
    setStatute(staff, ana.getNumber(), statute("WORKING_STUDENT", newEndDate))
        .andExpect(status().isOk());

    // Assert
    record()
        .andExpect(jsonPath("$.statuteEndDate").value(newEndDate.toString()));
  }

  @Test
  @DisplayName("A statute ending today is accepted")
  void endingTodayIsAccepted() throws Exception {
    setStatute(staff, ana.getNumber(), statute("STUDENT_PARENT", LocalDate.now()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.statuteType").value("STUDENT_PARENT"));
  }

  // ending

  @Test
  @DisplayName("Ending a statute is 204 and the record no longer has one")
  void staffEndsStatute() throws Exception {
    // Arrange
    setStatute(staff, ana.getNumber(), statute("WORKING_STUDENT", LocalDate.now().plusMonths(1)))
        .andExpect(status().isOk());

    // Act
    removeStatute(staff, ana.getNumber())
        .andExpect(status().isNoContent());

    // Assert
    record()
        .andExpect(jsonPath("$.statuteType").value(Matchers.nullValue()))
        .andExpect(jsonPath("$.statuteEndDate").value(Matchers.nullValue()));
  }

  @Test
  @DisplayName("Ending the statute of a student who has none is still 204")
  void endingMissingStatute() throws Exception {
    removeStatute(staff, ana.getNumber())
        .andExpect(status().isNoContent());
  }

  // errors

  @Test
  @DisplayName("An end date of yesterday is 400 STATUTE_END_DATE_IN_PAST and the current statute stays")
  void endDateInPast() throws Exception {
    // Arrange
    LocalDate currentEndDate = LocalDate.now().plusMonths(1);
    setStatute(staff, ana.getNumber(), statute("WORKING_STUDENT", currentEndDate)).andExpect(status().isOk());

    // Act
    setStatute(staff, ana.getNumber(), statute("STUDENT_PARENT", LocalDate.now().minusDays(1)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("STATUTE_END_DATE_IN_PAST"));

    // Assert
    record()
        .andExpect(jsonPath("$.statuteType").value("WORKING_STUDENT"))
        .andExpect(jsonPath("$.statuteEndDate").value(currentEndDate.toString()));
  }

  @Test
  @DisplayName("Without an end date it's 400 VALIDATION_FAILED on endDate")
  void missingEndDate() throws Exception {
    setStatute(staff, ana.getNumber(), "{\"type\":\"WORKING_STUDENT\"}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.fieldErrors[?(@.field == 'endDate')].code").value("NOT_NULL"));
  }

  @Test
  @DisplayName("A type that doesn't exist is 400 VALIDATION_FAILED on type")
  void unknownType() throws Exception {
    setStatute(staff, ana.getNumber(), statute("ASTRONAUT", LocalDate.now()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.fieldErrors[?(@.field == 'type')].code").value("INVALID"));
  }

  @Test
  @DisplayName("An unknown student is 404 NOT_FOUND, adding and ending")
  void unknownStudent() throws Exception {
    setStatute(staff, 999999999L, statute("WORKING_STUDENT", LocalDate.now()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    removeStatute(staff, 999999999L)
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
  }

  // who can edit it

  @Test
  @DisplayName("A teacher can't add or end a statute (403 ACCESS_DENIED)")
  void teacherDenied() throws Exception {
    // Arrange
    MockHttpSession teacher = loginAs(TEACHER);
    Student withStatute = studentRepository.findById(ana.getNumber()).orElseThrow();
    withStatute.assignStatute(StatuteType.WORKING_STUDENT, LocalDate.now().plusMonths(1));

    // Act + Assert
    setStatute(teacher, ana.getNumber(), statute("STUDENT_PARENT", LocalDate.now()))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    removeStatute(teacher, ana.getNumber())
        .andExpect(status().isForbidden());
    record()
        .andExpect(jsonPath("$.statuteType").value("WORKING_STUDENT"));
  }

  @Test
  @DisplayName("Not logged in is 401 UNAUTHENTICATED")
  void notLoggedIn() throws Exception {
    mockMvc.perform(put("/students/" + ana.getNumber() + "/statute").with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content(statute("WORKING_STUDENT", LocalDate.now())))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
  }

}
