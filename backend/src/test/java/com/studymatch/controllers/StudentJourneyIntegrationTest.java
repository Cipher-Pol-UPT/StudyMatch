package com.studymatch.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;
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
import com.studymatch.utils.EmailSender;

// the brief's test 4, a whole business workflow: several requests, each taking an id the one before returned,
// from registering a student to reading their grade back (docs/testing.md)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class StudentJourneyIntegrationTest {

  private static final String PASSWORD = "Test1234";
  private static final String STAFF = "it.journey.staff@studymatch.com";
  private static final String TEACHER = "it.journey.teacher@studymatch.com";

  @Autowired private MockMvc mockMvc;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private StaffRepository staffRepository;
  @Autowired private TeacherRepository teacherRepository;
  @Autowired private DegreeRepository degreeRepository;
  @Autowired private CategoryRepository categoryRepository;
  @Autowired private SubjectRepository subjectRepository;
  @Autowired private AcademicYearRepository academicYearRepository;
  @Autowired private SubjectEditionRepository subjectEditionRepository;

  // registering emails the credentials, here to a fake instead of a mail server
  @MockitoBean private EmailSender emailSender;

  private SubjectEdition edition;

  @BeforeEach
  void setUp() {
    Staff staff = new Staff("it.journey.staff@example.com", STAFF, "Test Staff", passwordEncoder.encode(PASSWORD));
    staff.setMustChangePassword(false);
    staffRepository.save(staff);
    Teacher teacher = new Teacher("it.journey.teacher@example.com", TEACHER, "Test Teacher",
        passwordEncoder.encode(PASSWORD));
    teacher.setMustChangePassword(false);
    teacher = teacherRepository.save(teacher);

    degreeRepository.save(new Degree("IT Journey Degree", 3));
    Category category = categoryRepository.save(new Category("IT Journey Category"));
    Subject subject = subjectRepository.save(new Subject("ITJOURNEY", "IT Journey Subject", 6, category));
    // the latest year, so it's the current one enrolments go into
    edition = new SubjectEdition(subject, academicYearRepository.save(new AcademicYear(2099)), Set.of(teacher));
    edition.addClassSection("T1");
    edition = subjectEditionRepository.save(edition);
  }

  private MockHttpSession loginAs(String email) throws Exception {
    MvcResult result = mockMvc.perform(post("/users/login").with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
        .andReturn();
    assertEquals(200, result.getResponse().getStatus());
    return (MockHttpSession) result.getRequest().getSession(false);
  }

  private static <T> T read(MvcResult result, String path) throws Exception {
    return JsonPath.read(result.getResponse().getContentAsString(), path);
  }

  @Test
  @DisplayName("Register, enrol, add an assignment and grade it: the student's record shows the grade they passed with")
  void registerEnrolGradeAndReadBack() throws Exception {
    MockHttpSession staff = loginAs(STAFF);
    MockHttpSession teacher = loginAs(TEACHER);

    // 1. the staff registers a student, the response has their new number
    MvcResult registered = mockMvc.perform(post("/students/").with(csrf()).session(staff)
        .contentType(MediaType.APPLICATION_JSON)
        .content("""
            {"name":"Journey Student","personalEmail":"it.journey.student@example.com",
             "degreeProgramme":"IT Journey Degree","curricularYear":1}"""))
        .andExpect(status().isCreated())
        .andReturn();
    Number number = read(registered, "$.number");

    // 2. the staff enrols that number in the edition's class
    mockMvc.perform(post("/subject-editions/" + edition.getId() + "/enrolments").with(csrf()).session(staff)
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"classSection\":\"T1\",\"studentNumbers\":[" + number + "]}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].number").value(number))
        .andExpect(jsonPath("$[0].result").value("ENROLLED"));

    // 3. the teacher adds the edition's only assignment, worth everything, the response has its id
    MvcResult assignment = mockMvc.perform(post("/subject-editions/" + edition.getId() + "/assignments")
        .with(csrf()).session(teacher)
        .contentType(MediaType.APPLICATION_JSON)
        .content("""
            {"name":"Exam","type":"EXAM","date":"2100-01-15","weight":100}"""))
        .andExpect(status().isCreated())
        .andReturn();
    Number assignmentId = read(assignment, "$.id");

    // 4. the teacher grades the student on it, which sets the final grade
    mockMvc.perform(put("/subject-editions/" + edition.getId() + "/assignments/" + assignmentId + "/grades/"
        + number).with(csrf()).session(teacher)
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"value\":15}"))
        .andExpect(status().isOk());

    // 5. the staff reads the student's record back
    mockMvc.perform(get("/students/" + number).session(staff))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.profile.name").value("Journey Student"))
        .andExpect(jsonPath("$.averageGrade").value(15.0))
        .andExpect(jsonPath("$.enrolments.length()").value(1))
        .andExpect(jsonPath("$.enrolments[0].subjectCode").value("ITJOURNEY"))
        .andExpect(jsonPath("$.enrolments[0].classSection").value("T1"))
        .andExpect(jsonPath("$.enrolments[0].status").value("PASSED"))
        .andExpect(jsonPath("$.enrolments[0].finalGrade").value(15));
  }

}
