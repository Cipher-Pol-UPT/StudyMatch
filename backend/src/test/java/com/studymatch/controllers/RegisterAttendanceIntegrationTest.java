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
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.studymatch.enums.StatuteType;
import com.studymatch.models.AcademicYear;
import com.studymatch.models.Category;
import com.studymatch.models.Degree;
import com.studymatch.models.Enrolment;
import com.studymatch.models.Staff;
import com.studymatch.models.Student;
import com.studymatch.models.Subject;
import com.studymatch.models.SubjectEdition;
import com.studymatch.models.Teacher;
import com.studymatch.repositories.AbsenceRepository;
import com.studymatch.repositories.AcademicYearRepository;
import com.studymatch.repositories.CategoryRepository;
import com.studymatch.repositories.DegreeRepository;
import com.studymatch.repositories.EnrolmentRepository;
import com.studymatch.repositories.LessonRepository;
import com.studymatch.repositories.StaffRepository;
import com.studymatch.repositories.StudentRepository;
import com.studymatch.repositories.SubjectEditionRepository;
import com.studymatch.repositories.SubjectRepository;
import com.studymatch.repositories.TeacherRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RegisterAttendanceIntegrationTest {

  private static final String PASSWORD = "Test1234";
  private static final String STAFF = "it.attendance.staff@studymatch.com";
  private static final String TEACHER = "it.attendance.teacher@studymatch.com";
  private static final String LESSON = "2026-11-10T09:00";

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
  @Autowired private LessonRepository lessonRepository;
  @Autowired private AbsenceRepository absenceRepository;

  private SubjectEdition edition;
  private SubjectEdition lastYear;
  private Student ana;
  private Student rui;
  private Student eva;

  // ana and rui (with a statute) are in T1, eva in T2; 2092 is the current year, 2091 the one before
  @BeforeEach
  void setUp() {
    staffRepository.save(withoutPasswordChange(new Staff("it.attendance.staff@example.com", STAFF, "Test Staff",
        passwordEncoder.encode(PASSWORD))));
    Teacher teacher = teacherRepository.save(withoutPasswordChange(new Teacher("it.attendance.teacher@example.com",
        TEACHER, "Test Teacher", passwordEncoder.encode(PASSWORD))));

    Degree degree = degreeRepository.save(new Degree("IT Attendance Degree", 3));
    Category category = categoryRepository.save(new Category("IT Attendance Category"));
    Subject subject = subjectRepository.save(new Subject("ITATTEND1", "IT Attendance Subject", 6, category));

    lastYear = edition(subject, 2091, teacher);
    edition = edition(subject, 2092, teacher);

    ana = studentRepository.save(new Student("it.attendance.ana@example.com", "Ana Attendance", "hash", degree, 1));
    rui = new Student("it.attendance.rui@example.com", "Rui Attendance", "hash", degree, 1);
    rui.assignStatute(StatuteType.WORKING_STUDENT, LocalDate.now().plusMonths(6));
    rui = studentRepository.save(rui);
    eva = studentRepository.save(new Student("it.attendance.eva@example.com", "Eva Attendance", "hash", degree, 1));
    enrolmentRepository.save(new Enrolment(ana, edition.getClassSection("T1")));
    enrolmentRepository.save(new Enrolment(rui, edition.getClassSection("T1")));
    enrolmentRepository.save(new Enrolment(eva, edition.getClassSection("T2")));
  }

  private SubjectEdition edition(Subject subject, int startYear, Teacher teacher) {
    SubjectEdition edition = new SubjectEdition(subject, academicYearRepository.save(new AcademicYear(startYear)),
        Set.of(teacher));
    edition.addClassSection("T1");
    edition.addClassSection("T2");
    return subjectEditionRepository.save(edition);
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

  private ResultActions sheet(String email, String classSection, String startsAt) throws Exception {
    return mockMvc.perform(get("/subject-editions/" + edition.getId() + "/attendance")
        .param("classSection", classSection)
        .param("startsAt", startsAt)
        .session(loginAs(email)));
  }

  private ResultActions sheet(String startsAt) throws Exception {
    return sheet(STAFF, "T1", startsAt);
  }

  // absences as JSON, e.g. {"studentNumber":1,"justified":false}
  private ResultActions register(String email, SubjectEdition edition, String classSection, String startsAt,
      String absences) throws Exception {
    return mockMvc.perform(put("/subject-editions/" + edition.getId() + "/attendance").with(csrf())
        .session(loginAs(email))
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"classSection\":\"" + classSection + "\",\"startsAt\":\"" + startsAt + "\",\"absences\":["
            + absences + "]}"));
  }

  private ResultActions register(String startsAt, String absences) throws Exception {
    return register(STAFF, edition, "T1", startsAt, absences);
  }

  private ResultActions deleteLesson(String email, SubjectEdition edition, String startsAt) throws Exception {
    return mockMvc.perform(delete("/subject-editions/" + edition.getId() + "/attendance").with(csrf())
        .param("classSection", "T1")
        .param("startsAt", startsAt)
        .session(loginAs(email)));
  }

  private static String absent(Student student, boolean justified) {
    return "{\"studentNumber\":" + student.getNumber() + ",\"justified\":" + justified + "}";
  }

  // only this test's class, the database can already have other lessons
  private long lessonsOfT1() {
    return lessonRepository.countByClassSection(edition.getClassSection("T1"));
  }

  private int absencesOfLesson() {
    return lessonRepository.findByClassSectionAndStartsAt(edition.getClassSection("T1"), LocalDateTime.parse(LESSON))
        .map(lesson -> absenceRepository.findByLesson(lesson).size())
        .orElse(0);
  }

  // the sheet

  @Test
  @DisplayName("A new lesson shows the class by name, everyone present and nothing recorded")
  void sheetOfNewLesson() throws Exception {
    sheet(LESSON)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.classSection").value("T1"))
        .andExpect(jsonPath("$.startsAt").value("2026-11-10T09:00:00"))
        .andExpect(jsonPath("$.recorded").value(false))
        .andExpect(jsonPath("$.students.length()").value(2))
        .andExpect(jsonPath("$.students[0].name").value("Ana Attendance"))
        .andExpect(jsonPath("$.students[0].absent").value(false))
        .andExpect(jsonPath("$.students[0].attendance").value(Matchers.nullValue()))
        .andExpect(jsonPath("$.students[1].name").value("Rui Attendance"))
        .andExpect(jsonPath("$.students[1].statute").value(true));
  }

  // register attendance

  @Test
  @DisplayName("Registering an absence saves the lesson and shows it when the sheet is opened again")
  void registerAndReadBack() throws Exception {
    // Act
    register(LESSON, absent(ana, false))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.recorded").value(true))
        .andExpect(jsonPath("$.students[0].absent").value(true))
        .andExpect(jsonPath("$.students[0].justified").value(false))
        .andExpect(jsonPath("$.students[0].attendance").value(0.0));

    // Assert
    sheet(LESSON)
        .andExpect(jsonPath("$.recorded").value(true))
        .andExpect(jsonPath("$.students[0].absent").value(true))
        .andExpect(jsonPath("$.students[1].absent").value(false));
    assertEquals(1, lessonsOfT1());
    assertEquals(1, absencesOfLesson());
  }

  @Test
  @DisplayName("A justified absence counts as attended")
  void justifiedAbsenceCountsAsAttended() throws Exception {
    register(LESSON, absent(ana, true))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.students[0].justified").value(true))
        .andExpect(jsonPath("$.students[0].attendance").value(100.0));
  }

  @Test
  @DisplayName("Correcting a lesson reuses it, a student no longer marked loses the absence")
  void correctionReplacesTheRecord() throws Exception {
    // Arrange
    register(LESSON, absent(ana, false)).andExpect(status().isOk());

    // Act
    register(LESSON, "")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.students[0].absent").value(false))
        .andExpect(jsonPath("$.students[0].attendance").value(100.0));

    // Assert
    assertEquals(1, lessonsOfT1());
    assertEquals(0, absencesOfLesson());
  }

  @Test
  @DisplayName("Seconds are dropped, 9:00:30 is the 9:00 lesson")
  void secondsAreDropped() throws Exception {
    // Arrange
    register("2026-11-10T09:00:30", absent(ana, false)).andExpect(status().isOk());

    // Act + Assert
    sheet(LESSON)
        .andExpect(jsonPath("$.recorded").value(true));
  }

  // delete a lesson

  @Test
  @DisplayName("Deleting a lesson removes it and its absences, so it stops counting for attendance")
  void deleteLessonWithAbsences() throws Exception {
    // Arrange
    register(LESSON, absent(ana, false)).andExpect(status().isOk());

    // Act
    deleteLesson(STAFF, edition, LESSON)
        .andExpect(status().isNoContent());

    // Assert
    assertEquals(0, lessonsOfT1());
    assertEquals(0, absenceRepository.findAll().stream()
        .filter(absence -> absence.getEnrolment().getStudent().getNumber().equals(ana.getNumber())).count());
    sheet(LESSON)
        .andExpect(jsonPath("$.recorded").value(false))
        .andExpect(jsonPath("$.students[0].absent").value(false))
        .andExpect(jsonPath("$.students[0].attendance").value(Matchers.nullValue()));
  }

  @Test
  @DisplayName("Deleting a lesson that was never registered is 404 NOT_FOUND")
  void deleteUnregisteredLesson() throws Exception {
    deleteLesson(STAFF, edition, LESSON)
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
  }

  @Test
  @DisplayName("Deleting a lesson of last year's edition is 409 SUBJECT_EDITION_NOT_CURRENT")
  void deleteLessonOfLastYear() throws Exception {
    deleteLesson(STAFF, lastYear, LESSON)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("SUBJECT_EDITION_NOT_CURRENT"));
  }

  @Test
  @DisplayName("A teacher can't delete a lesson, 403 ACCESS_DENIED")
  void teacherCantDeleteLesson() throws Exception {
    // Arrange
    register(LESSON, absent(ana, false)).andExpect(status().isOk());

    // Act + Assert
    deleteLesson(TEACHER, edition, LESSON)
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    assertEquals(1, lessonsOfT1());
  }

  // errors

  @Test
  @DisplayName("A lesson that hasn't started is 400 LESSON_IN_FUTURE, on the sheet and on saving")
  void futureLesson() throws Exception {
    // Arrange
    String nextMinute = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES).plusMinutes(1).toString();

    // Act + Assert
    sheet(nextMinute)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("LESSON_IN_FUTURE"));
    register(nextMinute, "")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("LESSON_IN_FUTURE"));
    assertEquals(0, lessonsOfT1());
  }

  @Test
  @DisplayName("A student of another class is 400 STUDENT_NOT_ENROLLED and nothing is saved")
  void studentOfAnotherClass() throws Exception {
    register(LESSON, absent(eva, false))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("STUDENT_NOT_ENROLLED"))
        .andExpect(jsonPath("$.params.number").value(eva.getNumber()));
    assertEquals(0, lessonsOfT1());
  }

  @Test
  @DisplayName("A class that isn't in the edition is 400 CLASS_SECTION_NOT_IN_SUBJECT_EDITION")
  void unknownClass() throws Exception {
    sheet(STAFF, "T9", LESSON)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("CLASS_SECTION_NOT_IN_SUBJECT_EDITION"));
  }

  @Test
  @DisplayName("Last year's edition is 409 SUBJECT_EDITION_NOT_CURRENT")
  void lastYearsEdition() throws Exception {
    register(STAFF, lastYear, "T1", LESSON, "")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("SUBJECT_EDITION_NOT_CURRENT"));
  }

  @Test
  @DisplayName("Without a start time the sheet is 400 VALIDATION_FAILED")
  void missingStart() throws Exception {
    mockMvc.perform(get("/subject-editions/" + edition.getId() + "/attendance")
        .param("classSection", "T1")
        .session(loginAs(STAFF)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
  }

  // who can register

  @Test
  @DisplayName("A teacher gets 403 ACCESS_DENIED, attendance is Staff's job")
  void teacherDenied() throws Exception {
    sheet(TEACHER, "T1", LESSON)
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    register(TEACHER, edition, "T1", LESSON, "")
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("Without logging in it's 401")
  void notLoggedIn() throws Exception {
    mockMvc.perform(get("/subject-editions/" + edition.getId() + "/attendance")
        .param("classSection", "T1")
        .param("startsAt", LESSON))
        .andExpect(status().isUnauthorized());
  }

}
