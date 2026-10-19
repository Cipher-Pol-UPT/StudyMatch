package com.studymatch.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mail.MailSendException;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;
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
import com.studymatch.repositories.UserRepository;
import com.studymatch.utils.EmailSender;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RegisterStudentIntegrationTest {

  private static final String PASSWORD = "Test1234";
  private static final String HEADER = "name,personalEmail,degreeProgramme,curricularYear\n";

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
  private UserRepository userRepository;

  @Autowired
  private PasswordEncoder passwordEncoder;

  // the real app, but emails go to this fake instead of a mail server
  @MockitoBean
  private EmailSender emailSender;

  private MockHttpSession staff;

  @BeforeEach
  void setUp() throws Exception {
    degreeRepository.save(new Degree("IT Test Degree", 3));
    Staff member = new Staff("it.staff@example.com", "it.staff@studymatch.com", "Test Staff", passwordEncoder.encode(PASSWORD));
    member.setMustChangePassword(false);
    staffRepository.save(member);
    staff = loginAs("it.staff@studymatch.com", PASSWORD);
  }

  private MockHttpSession loginAs(String email, String password) throws Exception {
    MvcResult result = mockMvc.perform(post("/users/login").with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
        .andReturn();
    assertEquals(200, result.getResponse().getStatus());
    return (MockHttpSession) result.getRequest().getSession(false);
  }

  private static String student(String email, int year) {
    return """
        {"name":"New Student","personalEmail":"%s","degreeProgramme":"IT Test Degree","curricularYear":%d}"""
        .formatted(email, year);
  }

  private ResultActions register(MockHttpSession session, String body) throws Exception {
    return mockMvc.perform(post("/students/").with(csrf()).session(session)
        .contentType(MediaType.APPLICATION_JSON)
        .content(body));
  }

  private ResultActions preview(String csv) throws Exception {
    MockMultipartFile file = new MockMultipartFile("file", "students.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));
    return mockMvc.perform(multipart("/students/import/preview").file(file).with(csrf()).session(staff));
  }

  private ResultActions importRows(String rowsJson) throws Exception {
    return mockMvc.perform(post("/students/import").with(csrf()).session(staff)
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"rows\":" + rowsJson + "}"));
  }

  // manual registration

  @Test
  @DisplayName("Full flow: staff registers a student, who logs in with the emailed password and hits both gates")
  void staffRegistersStudent() throws Exception {
    // Act
    MvcResult result = register(staff, student("new.student@example.com", 2))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.degreeProgramme").value("IT Test Degree"))
        .andExpect(jsonPath("$.curricularYear").value(2))
        .andExpect(jsonPath("$.emailSent").value(true))
        .andReturn();

    // Assert - number@students.domain, and the emailed password works
    Number number = JsonPath.read(result.getResponse().getContentAsString(), "$.number");
    String institutionalEmail = number + "@students.studymatch.com";
    assertEquals(institutionalEmail, JsonPath.read(result.getResponse().getContentAsString(), "$.institutionalEmail"));

    ArgumentCaptor<String> oneTimePassword = ArgumentCaptor.forClass(String.class);
    verify(emailSender).sendAccountCredentials(eq("new.student@example.com"), eq("New Student"), eq(institutionalEmail),
        oneTimePassword.capture());
    MockHttpSession newStudent = loginAs(institutionalEmail, oneTimePassword.getValue());
    mockMvc.perform(get("/users/me").session(newStudent))
        .andExpect(jsonPath("$.role").value("STUDENT"))
        .andExpect(jsonPath("$.mustChangePassword").value(true))
        .andExpect(jsonPath("$.personalityTestRequired").value(true));
  }

  @Test
  @DisplayName("A year the degree doesn't have is 400 and says how many years it has")
  void yearNotInDegree() throws Exception {
    register(staff, student("new.student@example.com", 4))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("CURRICULAR_YEAR_NOT_IN_DEGREE"))
        .andExpect(jsonPath("$.params.years").value(3));
  }

  @Test
  @DisplayName("Teachers and admins can't register students (403 ACCESS_DENIED)")
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
      register(loginAs(email, PASSWORD), student("new.student@example.com", 1))
          .andExpect(status().isForbidden())
          .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }
  }

  @Test
  @DisplayName("The degree list for the form has the name and number of years")
  void listDegrees() throws Exception {
    mockMvc.perform(get("/degrees/").session(staff))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.name == 'IT Test Degree')].years").value(3));
  }

  // resend

  @Test
  @DisplayName("When the email failed, staff can resend to a corrected address without a second account")
  void resendAfterFailedEmail() throws Exception {
    // Arrange - the first email fails, but the account is created
    doThrow(new MailSendException("mail server down"))
        .when(emailSender).sendAccountCredentials(eq("typo@exmaple.com"), any(), any(), any());
    MvcResult result = register(staff, student("typo@exmaple.com", 1))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.emailSent").value(false))
        .andReturn();
    Number number = JsonPath.read(result.getResponse().getContentAsString(), "$.number");
    long studentsBefore = studentRepository.count();

    // Act
    mockMvc.perform(post("/students/" + number + "/resend-credentials").with(csrf()).session(staff)
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"personalEmail\":\"fixed@example.com\"}"))
        .andExpect(status().isNoContent());

    // Assert
    verify(emailSender).sendAccountCredentials(eq("fixed@example.com"), any(), any(), any());
    assertEquals(studentsBefore, studentRepository.count());
  }

  @Test
  @DisplayName("Resending after the student set their own password is 409")
  void resendAfterPasswordChanged() throws Exception {
    // Arrange
    Student student = new Student("s@example.com", "Test Student", "hash", degreeRepository.findByNameIgnoreCase("IT Test Degree").orElseThrow(), 1);
    student.setMustChangePassword(false);
    student = studentRepository.save(student);

    // Act + Assert
    mockMvc.perform(post("/students/" + student.getNumber() + "/resend-credentials").with(csrf()).session(staff))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CREDENTIALS_ALREADY_USED"));
  }

  // CSV import

  @Test
  @DisplayName("The template downloads as a CSV with just the four columns")
  void template() throws Exception {
    mockMvc.perform(get("/students/import/template").session(staff))
        .andExpect(status().isOk())
        .andExpect(header().string("Content-Disposition", "attachment; filename=\"students.csv\""))
        .andExpect(content().contentTypeCompatibleWith("text/csv"))
        .andExpect(content().string("\uFEFFname,personalEmail,degreeProgramme,curricularYear\r\n"));
  }

  @Test
  @DisplayName("The preview reads quotes, commas and accents, flags bad rows by number, and creates nothing")
  void previewCreatesNothing() throws Exception {
    // Arrange
    String csv = HEADER
        + "\"Conceição, Ana\",ana.csv@example.com,IT Test Degree,1\n"
        + "Rui Test,rui.csv@example.com,Unknown Degree,1\n";

    // Act + Assert
    preview(csv)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].row").value(2))
        .andExpect(jsonPath("$[0].student.name").value("Conceição, Ana"))
        .andExpect(jsonPath("$[0].errors").isEmpty())
        .andExpect(jsonPath("$[1].row").value(3))
        .andExpect(jsonPath("$[1].errors[0].field").value("degreeProgramme"))
        .andExpect(jsonPath("$[1].errors[0].code").value("DEGREE_NOT_FOUND"));
    assertFalse(userRepository.existsByPersonalEmail("ana.csv@example.com"));
  }

  @Test
  @DisplayName("A file without the right columns is a file-level error")
  void previewWrongColumns() throws Exception {
    preview("nome,email\nAna,ana@example.com\n")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("CSV_MISSING_COLUMNS"));
  }

  @Test
  @DisplayName("Uploading without a file says which part is missing")
  void previewWithoutFile() throws Exception {
    mockMvc.perform(multipart("/students/import/preview").with(csrf()).session(staff))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.fieldErrors[0].field").value("file"));
  }

  @Test
  @DisplayName("Confirming creates the valid rows with unique numbers and reports each row")
  void importConfirmedRows() throws Exception {
    // Act - two good rows and one that broke since the preview (the degree name)
    MvcResult result = importRows("""
        [{"row":2,"student":{"name":"Ana Test","personalEmail":"ana.csv@example.com","degreeProgramme":"IT Test Degree","curricularYear":1}},
         {"row":3,"student":{"name":"Rui Test","personalEmail":"rui.csv@example.com","degreeProgramme":"IT Test Degree","curricularYear":2}},
         {"row":4,"student":{"name":"Eva Test","personalEmail":"eva.csv@example.com","degreeProgramme":"Gone Degree","curricularYear":1}}]""")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].status").value("CREATED"))
        .andExpect(jsonPath("$[1].status").value("CREATED"))
        .andExpect(jsonPath("$[2].status").value("REJECTED"))
        .andExpect(jsonPath("$[2].errors[0].code").value("DEGREE_NOT_FOUND"))
        .andReturn();

    // Assert
    String json = result.getResponse().getContentAsString();
    Number first = JsonPath.read(json, "$[0].number");
    Number second = JsonPath.read(json, "$[1].number");
    assertNotEquals(first, second);
    assertTrue(userRepository.existsByPersonalEmail("ana.csv@example.com"));
    assertFalse(userRepository.existsByPersonalEmail("eva.csv@example.com"));
  }

  @Test
  @DisplayName("A failed email in the import is reported apart from rejected rows, and the account exists")
  void importEmailFailure() throws Exception {
    // Arrange
    doThrow(new MailSendException("mail server down"))
        .when(emailSender).sendAccountCredentials(eq("ana.csv@example.com"), any(), any(), any());

    // Act + Assert
    importRows("""
        [{"row":2,"student":{"name":"Ana Test","personalEmail":"ana.csv@example.com","degreeProgramme":"IT Test Degree","curricularYear":1}}]""")
        .andExpect(jsonPath("$[0].status").value("EMAIL_NOT_SENT"))
        .andExpect(jsonPath("$[0].number").isNumber());
    assertTrue(userRepository.existsByPersonalEmail("ana.csv@example.com"));
  }

  @Test
  @DisplayName("Teachers can't import students either")
  void importOnlyStaff() throws Exception {
    // Arrange
    Teacher teacher = new Teacher("it.teacher@example.com", "it.teacher@studymatch.com", "Test Teacher",
        passwordEncoder.encode(PASSWORD));
    teacher.setMustChangePassword(false);
    teacherRepository.save(teacher);
    MockMultipartFile file = new MockMultipartFile("file", "students.csv", "text/csv", HEADER.getBytes(StandardCharsets.UTF_8));

    // Act + Assert
    mockMvc.perform(multipart("/students/import/preview").file(file).with(csrf())
        .session(loginAs("it.teacher@studymatch.com", PASSWORD)))
        .andExpect(status().isForbidden());
  }

}
