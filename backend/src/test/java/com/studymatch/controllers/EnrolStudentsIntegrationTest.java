package com.studymatch.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Set;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.studymatch.enums.EnrolmentStatus;
import com.studymatch.models.AcademicYear;
import com.studymatch.models.Category;
import com.studymatch.models.Degree;
import com.studymatch.models.Enrolment;
import com.studymatch.models.Staff;
import com.studymatch.models.Student;
import com.studymatch.models.Subject;
import com.studymatch.models.SubjectEdition;
import com.studymatch.models.Teacher;
import com.studymatch.repositories.AcademicYearRepository;
import com.studymatch.repositories.CategoryRepository;
import com.studymatch.repositories.DegreeRepository;
import com.studymatch.repositories.EnrolmentRepository;
import com.studymatch.repositories.StaffRepository;
import com.studymatch.repositories.StudentRepository;
import com.studymatch.repositories.SubjectEditionRepository;
import com.studymatch.repositories.SubjectRepository;
import com.studymatch.repositories.TeacherRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class EnrolStudentsIntegrationTest {

  private static final String PASSWORD = "Test1234";
  private static final String STAFF = "it.enrol.staff@studymatch.com";

  @Autowired private MockMvc mockMvc;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private StaffRepository staffRepository;
  @Autowired private TeacherRepository teacherRepository;
  @Autowired private DegreeRepository degreeRepository;
  @Autowired private CategoryRepository categoryRepository;
  @Autowired private SubjectRepository subjectRepository;
  @Autowired private AcademicYearRepository academicYearRepository;
  @Autowired private SubjectEditionRepository subjectEditionRepository;
  @Autowired private StudentRepository studentRepository;
  @Autowired private EnrolmentRepository enrolmentRepository;

  private SubjectEdition oldEdition;
  private SubjectEdition currentEdition;
  private Student ana;
  private Student rui;
  private Student eva;

  // same subject in 2097 (old) and 2098 (current, the newest year): rui passed it in 2097, eva failed it
  @BeforeEach
  void setUp() {
    staffRepository.save(withoutPasswordChange(new Staff("it.enrol.staff@example.com", STAFF, "Test Staff",
        passwordEncoder.encode(PASSWORD))));
    Teacher teacher = teacherRepository.save(withoutPasswordChange(new Teacher("it.enrol.teacher@example.com",
        "it.enrol.teacher@studymatch.com", "Test Teacher", passwordEncoder.encode(PASSWORD))));

    Degree degree = degreeRepository.save(new Degree("IT Enrol Degree", 3));
    Category category = categoryRepository.save(new Category("IT Enrol Category"));
    Subject subject = subjectRepository.save(new Subject("ITENROL1", "IT Enrol Subject", 6, category));

    oldEdition = edition(subject, academicYearRepository.save(new AcademicYear(2097)), teacher);
    currentEdition = edition(subject, academicYearRepository.save(new AcademicYear(2098)), teacher);

    ana = studentRepository.save(new Student("it.enrol.ana@example.com", "Ana Enrol", "hash", degree, 1));
    rui = studentRepository.save(new Student("it.enrol.rui@example.com", "Rui Enrol", "hash", degree, 1));
    eva = studentRepository.save(new Student("it.enrol.eva@example.com", "Eva Enrol", "hash", degree, 1));
    finished(rui, 15);
    finished(eva, 5);
  }

  private SubjectEdition edition(Subject subject, AcademicYear year, Teacher teacher) {
    SubjectEdition edition = new SubjectEdition(subject, year, Set.of(teacher));
    edition.addClassSection("T1");
    edition.addClassSection("T2");
    return subjectEditionRepository.save(edition);
  }

  private void finished(Student student, int finalGrade) {
    Enrolment enrolment = new Enrolment(student, oldEdition.getClassSection("T1"));
    enrolment.setFinalGrade(finalGrade);
    enrolmentRepository.save(enrolment);
  }

  private static <T extends com.studymatch.models.User> T withoutPasswordChange(T user) {
    user.setMustChangePassword(false);
    return user;
  }

  private MockHttpSession loginAs(String email) throws Exception {
    MvcResult result = mockMvc.perform(post("/users/login").with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
        .andReturn();
    assertEquals(200, result.getResponse().getStatus());
    return (MockHttpSession) result.getRequest().getSession(false);
  }

  private ResultActions enrol(String email, Long editionId, String classSection, Long... numbers) throws Exception {
    String list = String.join(",", Arrays.stream(numbers).map(String::valueOf).toList());
    return mockMvc.perform(post("/subject-editions/" + editionId + "/enrolments").with(csrf())
        .session(loginAs(email))
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"classSection\":\"" + classSection + "\",\"studentNumbers\":[" + list + "]}"));
  }

  private ResultActions enrol(String classSection, Long... numbers) throws Exception {
    return enrol(STAFF, currentEdition.getId(), classSection, numbers);
  }

  @Test
  @DisplayName("Staff gets only the current year's editions, with their classes")
  void staffListsCurrentEditions() throws Exception {
    mockMvc.perform(get("/subject-editions/current").session(loginAs(STAFF)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].id").value(currentEdition.getId()))
        .andExpect(jsonPath("$[0].academicYear").value(2098))
        .andExpect(jsonPath("$[0].subject.code").value("ITENROL1"))
        .andExpect(jsonPath("$[0].classSections[0]").value("T1"));
  }

  @Test
  @DisplayName("A new student is enrolled in the chosen class with status in progress")
  void studentIsEnrolled() throws Exception {
    // Act
    enrol("T2", ana.getNumber())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].number").value(ana.getNumber()))
        .andExpect(jsonPath("$[0].name").value("Ana Enrol"))
        .andExpect(jsonPath("$[0].result").value("ENROLLED"))
        .andExpect(jsonPath("$[0].row").doesNotExist());

    // Assert
    Enrolment saved = enrolmentRepository.findBySubjectEditionIdAndStudent_Number(currentEdition.getId(),
        ana.getNumber()).orElseThrow();
    assertEquals("T2", saved.getClassSection().getCode());
    assertEquals(EnrolmentStatus.IN_PROGRESS, saved.getStatus());
  }

  @Test
  @DisplayName("Each student gets a result: already enrolled, already passed and unknown are skipped, failing before isn't")
  void mixedResults() throws Exception {
    // Arrange
    enrol("T1", ana.getNumber()).andExpect(status().isOk());

    // Act + Assert
    enrol("T1", ana.getNumber(), rui.getNumber(), eva.getNumber(), 99999999L)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].result").value("ALREADY_ENROLLED"))
        .andExpect(jsonPath("$[1].result").value("ALREADY_PASSED"))
        .andExpect(jsonPath("$[2].result").value("ENROLLED"))
        .andExpect(jsonPath("$[3].result").value("NOT_FOUND"))
        .andExpect(jsonPath("$[3].name").doesNotExist());
    assertFalse(enrolmentRepository.findBySubjectEditionIdAndStudent_Number(currentEdition.getId(), rui.getNumber())
        .isPresent());
  }

  @Test
  @DisplayName("Last year's edition is 409 SUBJECT_EDITION_NOT_CURRENT")
  void oldEditionIsRefused() throws Exception {
    enrol(STAFF, oldEdition.getId(), "T1", ana.getNumber())
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("SUBJECT_EDITION_NOT_CURRENT"));
  }

  @Test
  @DisplayName("A class the edition doesn't have is 400 CLASS_SECTION_NOT_IN_SUBJECT_EDITION")
  void unknownClassIsRefused() throws Exception {
    enrol("PL9", ana.getNumber())
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("CLASS_SECTION_NOT_IN_SUBJECT_EDITION"));
  }

  @Test
  @DisplayName("An edition that doesn't exist is 404")
  void unknownEdition() throws Exception {
    enrol(STAFF, 99999999L, "T1", ana.getNumber()).andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("An empty list of students is a field error")
  void noStudentsIsFieldError() throws Exception {
    enrol("T1")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors[0].field").value("studentNumbers"));
  }

  // CSV import

  private ResultActions importCsv(String csv, String classSection) throws Exception {
    MockMultipartFile file = new MockMultipartFile("file", "enrolments.csv", "text/csv",
        csv.getBytes(StandardCharsets.UTF_8));
    var request = multipart("/subject-editions/" + currentEdition.getId() + "/enrolments/import").file(file)
        .with(csrf()).session(loginAs(STAFF));
    return mockMvc.perform(classSection == null ? request : request.param("classSection", classSection));
  }

  @Test
  @DisplayName("CSV: every row gets its result and row number, only the valid new students are enrolled")
  void csvImport() throws Exception {
    // Arrange - row 4 isn't a number, row 5 repeats ana
    String csv = "studentNumber\n" + ana.getNumber() + "\n" + rui.getNumber() + "\nabc\n" + ana.getNumber()
        + "\n99999999\n";

    // Act + Assert
    importCsv(csv, "T1")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(5))
        .andExpect(jsonPath("$[0].row").value(2))
        .andExpect(jsonPath("$[0].result").value("ENROLLED"))
        .andExpect(jsonPath("$[1].result").value("ALREADY_PASSED"))
        .andExpect(jsonPath("$[2].row").value(4))
        .andExpect(jsonPath("$[2].result").value("INVALID_NUMBER"))
        .andExpect(jsonPath("$[2].number").doesNotExist())
        .andExpect(jsonPath("$[3].result").value("DUPLICATE_IN_FILE"))
        .andExpect(jsonPath("$[4].result").value("NOT_FOUND"));
    assertEquals("T1", enrolmentRepository.findBySubjectEditionIdAndStudent_Number(currentEdition.getId(),
        ana.getNumber()).orElseThrow().getClassSection().getCode());
  }

  @Test
  @DisplayName("CSV without the class is a NOT_NULL field error on classSection")
  void csvWithoutClass() throws Exception {
    importCsv("studentNumber\n" + ana.getNumber() + "\n", null)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors[0].field").value("classSection"))
        .andExpect(jsonPath("$.fieldErrors[0].code").value("NOT_NULL"));
  }

  @Test
  @DisplayName("CSV without the studentNumber column is 400 CSV_MISSING_COLUMNS")
  void csvWithoutColumn() throws Exception {
    importCsv("number\n1\n", "T1")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("CSV_MISSING_COLUMNS"));
  }

  @Test
  @DisplayName("The template downloads as a CSV with the studentNumber column")
  void csvTemplate() throws Exception {
    mockMvc.perform(get("/subject-editions/enrolments/import/template").session(loginAs(STAFF)))
        .andExpect(status().isOk())
        .andExpect(header().string("Content-Disposition", "attachment; filename=\"enrolments.csv\""))
        .andExpect(content().string(Matchers.endsWith("studentNumber\r\n")));
  }

  // who can enrol

  @Test
  @DisplayName("Teachers can't enrol students or list the current editions")
  void teacherDenied() throws Exception {
    enrol("it.enrol.teacher@studymatch.com", currentEdition.getId(), "T1", ana.getNumber())
        .andExpect(status().isForbidden());
    mockMvc.perform(get("/subject-editions/current").session(loginAs("it.enrol.teacher@studymatch.com")))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("Without logging in it's 401")
  void notLoggedIn() throws Exception {
    mockMvc.perform(post("/subject-editions/" + currentEdition.getId() + "/enrolments").with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"classSection\":\"T1\",\"studentNumbers\":[1]}"))
        .andExpect(status().isUnauthorized());
  }

}
