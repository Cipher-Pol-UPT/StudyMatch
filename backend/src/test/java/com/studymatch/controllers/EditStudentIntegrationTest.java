package com.studymatch.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

import com.studymatch.models.Admin;
import com.studymatch.models.Degree;
import com.studymatch.models.Staff;
import com.studymatch.models.Student;
import com.studymatch.models.Teacher;
import com.studymatch.repositories.AdminRepository;
import com.studymatch.repositories.DegreeRepository;
import com.studymatch.repositories.StaffRepository;
import com.studymatch.repositories.StudentRepository;
import com.studymatch.repositories.TeacherRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class EditStudentIntegrationTest {

  private static final String PASSWORD = "Test1234";

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private StaffRepository staffRepository;

  @Autowired
  private TeacherRepository teacherRepository;

  @Autowired
  private AdminRepository adminRepository;

  @Autowired
  private DegreeRepository degreeRepository;

  @Autowired
  private StudentRepository studentRepository;

  @Autowired
  private PasswordEncoder passwordEncoder;

  private MockHttpSession staff;
  private Degree threeYears;
  private Degree fiveYears;
  private Student ana;

  @BeforeEach
  void setUp() throws Exception {
    threeYears = degreeRepository.save(new Degree("IT Edit Test Degree", 3));
    fiveYears = degreeRepository.save(new Degree("IT Edit Other Degree", 5));
    ana = studentRepository.save(new Student("it.ana@example.com", "Ana Edit", "hash", threeYears, 2));
    studentRepository.save(new Student("it.rui@example.com", "Rui Edit", "hash", threeYears, 1));

    Staff member = new Staff("it.staff@example.com", "it.staff@studymatch.com", "Test Staff", passwordEncoder.encode(PASSWORD));
    member.setMustChangePassword(false);
    staffRepository.save(member);
    staff = loginAs("it.staff@studymatch.com");
  }

  private MockHttpSession loginAs(String email) throws Exception {
    MvcResult result = mockMvc.perform(post("/users/login").with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
        .andReturn();
    assertEquals(200, result.getResponse().getStatus());
    return (MockHttpSession) result.getRequest().getSession(false);
  }

  private static String changes(String name, String email, Long degreeId, int year) {
    return """
        {"name":"%s","personalEmail":"%s","degreeId":%d,"curricularYear":%d}"""
        .formatted(name, email, degreeId, year);
  }

  private ResultActions edit(MockHttpSession session, Long number, String body) throws Exception {
    return mockMvc.perform(put("/students/" + number).with(csrf()).session(session)
        .contentType(MediaType.APPLICATION_JSON)
        .content(body));
  }

  @Test
  @DisplayName("Staff edits a student and opening the student again shows the new data")
  void staffEditsStudent() throws Exception {
    // Act
    edit(staff, ana.getNumber(), changes("Ana Edited", "it.ana.new@example.com", fiveYears.getId(), 5))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.profile.name").value("Ana Edited"))
        .andExpect(jsonPath("$.personalEmail").value("it.ana.new@example.com"))
        .andExpect(jsonPath("$.profile.privateProfile.degree").value("IT Edit Other Degree"))
        .andExpect(jsonPath("$.profile.privateProfile.curricularYear").value(5));

    // Assert
    mockMvc.perform(get("/students/" + ana.getNumber()).session(staff))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.profile.name").value("Ana Edited"))
        .andExpect(jsonPath("$.personalEmail").value("it.ana.new@example.com"))
        .andExpect(jsonPath("$.profile.privateProfile.degree").value("IT Edit Other Degree"));
  }

  @Test
  @DisplayName("Saving with the student's own email unchanged works")
  void ownEmailKept() throws Exception {
    edit(staff, ana.getNumber(), changes("Ana Edited", "it.ana@example.com", threeYears.getId(), 3))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.personalEmail").value("it.ana@example.com"))
        .andExpect(jsonPath("$.profile.privateProfile.curricularYear").value(3));
  }

  @Test
  @DisplayName("A blank name and a broken email are field errors")
  void fieldErrors() throws Exception {
    edit(staff, ana.getNumber(), changes(" ", "not-an-email", threeYears.getId(), 1))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.fieldErrors[?(@.field == 'name')].code").value("NOT_BLANK"))
        .andExpect(jsonPath("$.fieldErrors[?(@.field == 'personalEmail')].code").value("EMAIL"));
  }

  @Test
  @DisplayName("An unknown student is 404 NOT_FOUND")
  void unknownStudent() throws Exception {
    edit(staff, 999999999L, changes("Ana Edited", "it.ana@example.com", threeYears.getId(), 1))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
  }

  @Test
  @DisplayName("An unknown degree is 400 DEGREE_NOT_FOUND")
  void unknownDegree() throws Exception {
    edit(staff, ana.getNumber(), changes("Ana Edited", "it.ana@example.com", 999999999L, 1))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("DEGREE_NOT_FOUND"));
  }

  @Test
  @DisplayName("A year the degree doesn't have is 400 and says how many years it has")
  void yearNotInDegree() throws Exception {
    edit(staff, ana.getNumber(), changes("Ana Edited", "it.ana@example.com", threeYears.getId(), 4))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("CURRICULAR_YEAR_NOT_IN_DEGREE"))
        .andExpect(jsonPath("$.params.years").value(3));
  }

  @Test
  @DisplayName("Another student's email is 409 PERSONAL_EMAIL_ALREADY_EXISTS and nothing is changed")
  void emailTaken() throws Exception {
    // Act
    edit(staff, ana.getNumber(), changes("Ana Edited", "it.rui@example.com", threeYears.getId(), 1))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("PERSONAL_EMAIL_ALREADY_EXISTS"));

    // Assert
    mockMvc.perform(get("/students/" + ana.getNumber()).session(staff))
        .andExpect(jsonPath("$.profile.name").value("Ana Edit"))
        .andExpect(jsonPath("$.personalEmail").value("it.ana@example.com"));
  }

  @Test
  @DisplayName("Teachers and admins can't edit students (403 ACCESS_DENIED)")
  void onlyStaff() throws Exception {
    // Arrange
    Teacher teacher = new Teacher("it.teacher@example.com", "it.teacher@studymatch.com", "Test Teacher",
        passwordEncoder.encode(PASSWORD));
    teacher.setMustChangePassword(false);
    teacherRepository.save(teacher);
    Admin admin = new Admin("it.admin@example.com", "it.admin@studymatch.com", "Test Admin", passwordEncoder.encode(PASSWORD));
    admin.setMustChangePassword(false);
    adminRepository.save(admin);

    // Act + Assert
    for (String email : new String[] { "it.teacher@studymatch.com", "it.admin@studymatch.com" }) {
      edit(loginAs(email), ana.getNumber(), changes("Ana Edited", "it.ana@example.com", threeYears.getId(), 1))
          .andExpect(status().isForbidden())
          .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }
  }

  @Test
  @DisplayName("Not logged in is 401 UNAUTHENTICATED")
  void notLoggedIn() throws Exception {
    mockMvc.perform(put("/students/" + ana.getNumber()).with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content(changes("Ana Edited", "it.ana@example.com", threeYears.getId(), 1)))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
  }

}
