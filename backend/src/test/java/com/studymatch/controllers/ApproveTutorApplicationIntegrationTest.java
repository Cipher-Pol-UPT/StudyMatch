package com.studymatch.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import com.studymatch.enums.NotificationTarget;
import com.studymatch.enums.NotificationType;
import com.studymatch.models.AcademicYear;
import com.studymatch.models.Category;
import com.studymatch.models.Degree;
import com.studymatch.models.Enrolment;
import com.studymatch.models.Notification;
import com.studymatch.models.PersonalityProfile;
import com.studymatch.models.Student;
import com.studymatch.models.Subject;
import com.studymatch.models.SubjectEdition;
import com.studymatch.models.Teacher;
import com.studymatch.models.TutorApplication;
import com.studymatch.models.User;
import com.studymatch.repositories.AcademicYearRepository;
import com.studymatch.repositories.CategoryRepository;
import com.studymatch.repositories.DegreeRepository;
import com.studymatch.repositories.EnrolmentRepository;
import com.studymatch.repositories.NotificationRepository;
import com.studymatch.repositories.StudentRepository;
import com.studymatch.repositories.SubjectEditionRepository;
import com.studymatch.repositories.SubjectRepository;
import com.studymatch.repositories.TeacherRepository;
import com.studymatch.repositories.TutorApplicationRepository;
import com.studymatch.repositories.TutorRoleRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ApproveTutorApplicationIntegrationTest {

  private static final String PASSWORD = "Test1234";

  @Autowired
  private MockMvc mockMvc;
  @Autowired
  private PasswordEncoder passwordEncoder;
  @Autowired
  private DegreeRepository degreeRepository;
  @Autowired
  private StudentRepository studentRepository;
  @Autowired
  private TeacherRepository teacherRepository;
  @Autowired
  private CategoryRepository categoryRepository;
  @Autowired
  private SubjectRepository subjectRepository;
  @Autowired
  private AcademicYearRepository academicYearRepository;
  @Autowired
  private SubjectEditionRepository subjectEditionRepository;
  @Autowired
  private EnrolmentRepository enrolmentRepository;
  @Autowired
  private TutorApplicationRepository tutorApplicationRepository;
  @Autowired
  private TutorRoleRepository tutorRoleRepository;
  @Autowired
  private NotificationRepository notificationRepository;

  private Teacher teacher;
  private Subject maths;
  private Student applicant;
  private TutorApplication application;
  private MockHttpSession teacherSession;

  @BeforeEach
  void setUp() throws Exception {
    teacher = teacherRepository.save(new Teacher("it.appr.teacher@example.com", "it.appr.teacher@studymatch.com",
        "Test Teacher", passwordEncoder.encode(PASSWORD)));
    teacher.setMustChangePassword(false);
    teacherRepository.saveAndFlush(teacher);

    Category category = categoryRepository.save(new Category("IT Approve Category"));
    maths = subjectRepository.save(new Subject("IT-APPR-MATH", "Test Maths", 6, category));

    AcademicYear year = academicYearRepository.save(new AcademicYear(2091));
    SubjectEdition edition = new SubjectEdition(maths, year, Set.of(teacher));
    edition.addClassSection("T1");
    edition = subjectEditionRepository.save(edition);

    applicant = saveStudent("it.appr.applicant@example.com", "Applicant Student");
    Enrolment enrolment = new Enrolment(applicant, edition.getClassSection("T1"));
    enrolment.setFinalGrade(18);
    enrolmentRepository.save(enrolment);

    application = tutorApplicationRepository.save(new TutorApplication(applicant, maths, "test motivation"));

    teacherSession = loginAndGetSession(teacher.getInstitutionalEmail());
  }

  private Student saveStudent(String personalEmail, String name) {
    Degree degree = degreeRepository.save(new Degree("IT Approve Degree " + name, 3));
    Student student = new Student(personalEmail, name, passwordEncoder.encode(PASSWORD), degree, 1);
    student.setMustChangePassword(false);
    student.setPersonalityProfile(new PersonalityProfile(0.5, 0.5, 0.5, 0.5, 0.5));
    student = studentRepository.save(student);
    student.generateInstitutionalEmail("studymatch.com");
    return studentRepository.saveAndFlush(student);
  }

  private MockHttpSession loginAndGetSession(String email) throws Exception {
    MvcResult result = mockMvc.perform(post("/users/login").with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
        .andReturn();
    assertEquals(200, result.getResponse().getStatus());
    return (MockHttpSession) result.getRequest().getSession(false);
  }

  @Test
  @DisplayName("The teacher lists the pending applications with grade, position and motivation")
  void teacherListsPendingApplications() throws Exception {
    mockMvc.perform(get("/tutor-applications/").with(csrf()).session(teacherSession)
        .param("subjectId", maths.getId().toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].studentNumber").value(applicant.getNumber()))
        .andExpect(jsonPath("$[0].studentName").value("Applicant Student"))
        .andExpect(jsonPath("$[0].finalGrade").value(18))
        .andExpect(jsonPath("$[0].position").value(1))
        .andExpect(jsonPath("$[0].motivation").value("test motivation"));
  }

  @Test
  @DisplayName("Approving creates the tutor role and sets the application to APPROVED")
  void teacherApproves() throws Exception {
    mockMvc.perform(post("/tutor-applications/" + application.getId() + "/approve").with(csrf()).session(teacherSession))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("APPROVED"));

    assertTrue(tutorRoleRepository.findByStudentNumberAndSubjectId(applicant.getNumber(), maths.getId()).isPresent());
  }

  @Test
  @DisplayName("Rejecting sets the application to REJECTED")
  void teacherRejects() throws Exception {
    mockMvc.perform(post("/tutor-applications/" + application.getId() + "/reject").with(csrf()).session(teacherSession)
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"reason\":\"not good enough\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("REJECTED"));
  }

  @Test
  @DisplayName("Rejecting without a body still works")
  void rejectsWithoutReason() throws Exception {
    // no body at all, so the request is null and the reason stays null
    mockMvc.perform(post("/tutor-applications/" + application.getId() + "/reject").with(csrf()).session(teacherSession))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("REJECTED"));
  }

  @Test
  @DisplayName("A decided application can't be decided again")
  void decidingTwiceIsRejected() throws Exception {
    // Arrange - approve once
    mockMvc.perform(post("/tutor-applications/" + application.getId() + "/approve").with(csrf()).session(teacherSession))
        .andExpect(status().isOk());

    // Act + Assert - reject again
    mockMvc.perform(post("/tutor-applications/" + application.getId() + "/reject").with(csrf()).session(teacherSession)
        .contentType(MediaType.APPLICATION_JSON)
        .content("{}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("APPLICATION_ALREADY_DECIDED"));
  }

  @Test
  @DisplayName("A teacher who doesn't teach the subject's current edition can't decide")
  void notTeacherCannotApprove() throws Exception {
    // Arrange
    Teacher other = teacherRepository.save(new Teacher("it.appr.other@example.com", "it.appr.other@studymatch.com",
        "Other Teacher", passwordEncoder.encode(PASSWORD)));
    other.setMustChangePassword(false);
    teacherRepository.saveAndFlush(other);
    MockHttpSession otherSession = loginAndGetSession(other.getInstitutionalEmail());

    // Act + Assert
    mockMvc.perform(post("/tutor-applications/" + application.getId() + "/approve").with(csrf()).session(otherSession))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
  }

  @Test
  @DisplayName("Only teachers can decide applications")
  void studentCannotApprove() throws Exception {
    // Arrange
    MockHttpSession studentSession = loginAndGetSession(applicant.getInstitutionalEmail());

    // Act + Assert
    mockMvc.perform(post("/tutor-applications/" + application.getId() + "/approve").with(csrf()).session(studentSession))
        .andExpect(status().isForbidden());
  }

  private List<Notification> notificationsOf(User user) {
    return notificationRepository.findByRecipientNumber(user.getNumber(), Pageable.unpaged()).getContent();
  }

  @Test
  @DisplayName("Approving tells the student, pointing at the application")
  void approvingNotifiesStudent() throws Exception {
    // Act
    mockMvc.perform(post("/tutor-applications/" + application.getId() + "/approve").with(csrf()).session(teacherSession))
        .andExpect(status().isOk());

    // Assert
    List<Notification> notifications = notificationsOf(applicant);
    assertEquals(1, notifications.size());
    assertEquals(NotificationType.TUTOR_APPLICATION_APPROVED, notifications.getFirst().getType());
    assertEquals(NotificationTarget.TUTOR_APPLICATION, notifications.getFirst().getTarget());
    assertEquals(application.getId(), notifications.getFirst().getTargetId());
    assertEquals("Test Maths", notifications.getFirst().getParams().get("subject"));
  }

  @Test
  @DisplayName("Rejecting tells the student, with the reason")
  void rejectingNotifiesStudentWithReason() throws Exception {
    // Act
    mockMvc.perform(post("/tutor-applications/" + application.getId() + "/reject").with(csrf()).session(teacherSession)
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"reason\":\"not good enough\"}"))
        .andExpect(status().isOk());

    // Assert
    List<Notification> notifications = notificationsOf(applicant);
    assertEquals(1, notifications.size());
    assertEquals(NotificationType.TUTOR_APPLICATION_REJECTED, notifications.getFirst().getType());
    assertEquals("not good enough", notifications.getFirst().getParams().get("reason"));
  }

}
