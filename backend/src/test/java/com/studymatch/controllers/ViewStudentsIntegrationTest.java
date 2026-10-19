package com.studymatch.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalDateTime;
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

import com.studymatch.enums.AccountStatus;
import com.studymatch.enums.StatuteType;
import com.studymatch.models.AcademicYear;
import com.studymatch.models.Category;
import com.studymatch.models.ClassSection;
import com.studymatch.models.Degree;
import com.studymatch.models.Enrolment;
import com.studymatch.models.Lesson;
import com.studymatch.models.Staff;
import com.studymatch.models.Student;
import com.studymatch.models.Subject;
import com.studymatch.models.SubjectEdition;
import com.studymatch.models.Teacher;
import com.studymatch.models.Absence;
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
class ViewStudentsIntegrationTest {

  private static final String PASSWORD = "Test1234";

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

  private Degree degree;
  private SubjectEdition edition;
  private Student ana;
  private Student rui;
  private Enrolment anaEnrolment;

  // ana: class T1, 14 in a finished 6 ECTS subject, 1 absence in 2 lessons. rui: class T2, still in progress
  @BeforeEach
  void setUp() {
    staffRepository.save(withoutPasswordChange(new Staff("it.staff@example.com", "it.staff@studymatch.com",
        "Test Staff", passwordEncoder.encode(PASSWORD))));
    Teacher teacher = teacherRepository.save(withoutPasswordChange(new Teacher("it.teacher@example.com",
        "it.teacher@studymatch.com", "Test Teacher", passwordEncoder.encode(PASSWORD))));
    teacherRepository.save(withoutPasswordChange(new Teacher("it.other@example.com", "it.other@studymatch.com",
        "Other Teacher", passwordEncoder.encode(PASSWORD))));

    degree = degreeRepository.save(new Degree("IT View Degree", 3));
    Category category = categoryRepository.save(new Category("IT View Category"));
    Subject subject = subjectRepository.save(new Subject("ITVIEW1", "IT View Subject", 6, category));
    AcademicYear year = academicYearRepository.save(new AcademicYear(2091));

    edition = new SubjectEdition(subject, year, Set.of(teacher));
    edition.addClassSection("T1");
    edition.addClassSection("T2");
    edition = subjectEditionRepository.save(edition);

    ana = studentRepository.save(new Student("it.ana@example.com", "Ana View", "hash", degree, 2));
    rui = studentRepository.save(new Student("it.rui@example.com", "Rui View", "hash", degree, 1));

    Enrolment enrolment = new Enrolment(ana, section("T1"));
    enrolment.setFinalGrade(14);
    anaEnrolment = enrolmentRepository.save(enrolment);
    enrolmentRepository.save(new Enrolment(rui, section("T2")));

    Lesson first = lessonRepository.save(new Lesson(section("T1"), LocalDateTime.now().minusDays(2)));
    lessonRepository.save(new Lesson(section("T1"), LocalDateTime.now().minusDays(1)));
    absenceRepository.save(new Absence(first, anaEnrolment, false));
  }

  private static <T extends com.studymatch.models.User> T withoutPasswordChange(T user) {
    user.setMustChangePassword(false);
    return user;
  }

  private ClassSection section(String code) {
    return edition.getClassSections().stream().filter(section -> section.getCode().equals(code)).findFirst().orElseThrow();
  }

  private MockHttpSession loginAs(String email) throws Exception {
    MvcResult result = mockMvc.perform(post("/users/login").with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
        .andReturn();
    assertEquals(200, result.getResponse().getStatus());
    return (MockHttpSession) result.getRequest().getSession(false);
  }

  private ResultActions staffList(String query) throws Exception {
    return mockMvc.perform(get("/students/?degreeId=" + degree.getId() + query).session(loginAs("it.staff@studymatch.com")));
  }

  // staff: the list

  @Test
  @DisplayName("Staff sees every student with year, attendance, average grade and status, sorted by name")
  void staffList() throws Exception {
    staffList("")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(2))
        .andExpect(jsonPath("$.content[0].name").value("Ana View"))
        .andExpect(jsonPath("$.content[0].curricularYear").value(2))
        .andExpect(jsonPath("$.content[0].attendance").value(50.0))
        .andExpect(jsonPath("$.content[0].averageGrade").value(14.0))
        .andExpect(jsonPath("$.content[0].accountStatus").value("ACTIVE"))
        .andExpect(jsonPath("$.content[1].name").value("Rui View"))
        .andExpect(jsonPath("$.content[1].averageGrade").doesNotExist());
  }

  @Test
  @DisplayName("Filters can be combined, and search finds part of a name or the exact number")
  void staffFilters() throws Exception {
    staffList("&curricularYear=1").andExpect(jsonPath("$.content[*].name").value("Rui View"));
    staffList("&search=ana").andExpect(jsonPath("$.content[*].name").value("Ana View"));
    staffList("&search=" + rui.getNumber()).andExpect(jsonPath("$.content[*].name").value("Rui View"));
    staffList("&search=nobody").andExpect(jsonPath("$.totalElements").value(0));
  }

  private ResultActions staffSearch(String search) throws Exception {
    return mockMvc.perform(get("/students/").param("degreeId", degree.getId().toString()).param("search", search)
        .session(loginAs("it.staff@studymatch.com")));
  }

  @Test
  @DisplayName("% and _ in the search are normal characters, not wildcards")
  void staffSearchWildcards() throws Exception {
    // Arrange
    studentRepository.save(new Student("it.eva@example.com", "Eva_Maria View", "hash", degree, 1));

    // Act + Assert - as wildcards, both would match all 3 students
    staffSearch("_").andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].name").value("Eva_Maria View"));
    staffSearch("%").andExpect(jsonPath("$.totalElements").value(0));
  }

  @Test
  @DisplayName("Blocked accounts can be filtered by status")
  void staffFilterByStatus() throws Exception {
    rui.setAccountStatus(AccountStatus.BLOCKED);
    staffList("&status=BLOCKED").andExpect(jsonPath("$.content[*].name").value("Rui View"));
  }

  @Test
  @DisplayName("20 students per page: 21 students is 2 pages, the second with 1 (boundary)")
  void staffPagination() throws Exception {
    // Arrange - 19 more, 21 in total
    for (int i = 0; i < 19; i++) {
      studentRepository.save(new Student("it.page" + i + "@example.com", "Page Student " + i, "hash", degree, 1));
    }

    // Act + Assert
    staffList("").andExpect(jsonPath("$.content.length()").value(20)).andExpect(jsonPath("$.totalPages").value(2));
    staffList("&page=1").andExpect(jsonPath("$.content.length()").value(1));
  }

  @Test
  @DisplayName("A status that doesn't exist is a field error, not a crash")
  void staffWrongStatus() throws Exception {
    staffList("&status=SLEEPING")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.fieldErrors[0].field").value("status"))
        .andExpect(jsonPath("$.fieldErrors[0].code").value("INVALID"));
  }

  @Test
  @DisplayName("Teachers can't see the full student list")
  void listOnlyStaff() throws Exception {
    mockMvc.perform(get("/students/").session(loginAs("it.teacher@studymatch.com")))
        .andExpect(status().isForbidden());
  }

  // staff: one student's record

  @Test
  @DisplayName("Staff's record has the enrolments, statute type, average grade and category levels")
  void staffRecord() throws Exception {
    // Arrange
    ana.assignStatute(StatuteType.WORKING_STUDENT, LocalDate.now().plusMonths(3));

    // Act + Assert
    mockMvc.perform(get("/students/" + ana.getNumber()).session(loginAs("it.staff@studymatch.com")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.profile.name").value("Ana View"))
        .andExpect(jsonPath("$.personalEmail").value("it.ana@example.com"))
        .andExpect(jsonPath("$.statuteType").value("WORKING_STUDENT"))
        .andExpect(jsonPath("$.averageGrade").value(14.0))
        .andExpect(jsonPath("$.categoryLevels[0].category").value("IT View Category"))
        .andExpect(jsonPath("$.categoryLevels[0].level").value(14.0))
        .andExpect(jsonPath("$.enrolments[0].subjectCode").value("ITVIEW1"))
        .andExpect(jsonPath("$.enrolments[0].classSection").value("T1"))
        .andExpect(jsonPath("$.enrolments[0].status").value("PASSED"))
        .andExpect(jsonPath("$.enrolments[0].finalGrade").value(14));
  }

  @Test
  @DisplayName("Unknown and non-numeric student numbers are 404 and 400")
  void staffRecordBadNumbers() throws Exception {
    MockHttpSession staff = loginAs("it.staff@studymatch.com");
    mockMvc.perform(get("/students/99999999").session(staff)).andExpect(status().isNotFound());
    mockMvc.perform(get("/students/abc").session(staff))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors[0].field").value("number"));
  }

  @Test
  @DisplayName("Teachers can't open the staff record (it has the statute type)")
  void recordOnlyStaff() throws Exception {
    mockMvc.perform(get("/students/" + ana.getNumber()).session(loginAs("it.teacher@studymatch.com")))
        .andExpect(status().isForbidden());
  }

  // teacher: students of one edition

  private ResultActions teacherList(String email, String query) throws Exception {
    return mockMvc.perform(get("/subject-editions/" + edition.getId() + "/students" + query).session(loginAs(email)));
  }

  @Test
  @DisplayName("The edition's teacher sees its students with class, category level and attendance in that subject")
  void teacherSeesEditionStudents() throws Exception {
    teacherList("it.teacher@studymatch.com", "")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[0].name").value("Ana View"))
        .andExpect(jsonPath("$[0].classSection").value("T1"))
        .andExpect(jsonPath("$[0].categoryLevel").value(14.0))
        .andExpect(jsonPath("$[0].attendance").value(50.0))
        .andExpect(jsonPath("$[1].name").value("Rui View"))
        .andExpect(jsonPath("$[1].categoryLevel").doesNotExist());
  }

  @Test
  @DisplayName("The teacher can filter by class")
  void teacherFiltersByClass() throws Exception {
    teacherList("it.teacher@studymatch.com", "?classSection=T2")
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].name").value("Rui View"));
  }

  @Test
  @DisplayName("After a class change attendance only counts the new class: 2 old absences and 1 new lesson is 100%, not -100%")
  void attendanceAfterClassChange() throws Exception {
    // Arrange - a 2nd absence in T1, then ana moves to T2, which has had 1 lesson
    Lesson third = lessonRepository.save(new Lesson(section("T1"), LocalDateTime.now().minusDays(3)));
    absenceRepository.save(new Absence(third, anaEnrolment, false));
    lessonRepository.save(new Lesson(section("T2"), LocalDateTime.now().minusHours(1)));
    anaEnrolment.changeClassSection(section("T2"));

    // Act + Assert - same number for the teacher and for staff
    teacherList("it.teacher@studymatch.com", "?search=ana")
        .andExpect(jsonPath("$[0].classSection").value("T2"))
        .andExpect(jsonPath("$[0].attendance").value(100.0));
    staffList("&search=ana").andExpect(jsonPath("$.content[0].attendance").value(100.0));
  }

  @Test
  @DisplayName("A teacher who doesn't teach the edition gets 403 ACCESS_DENIED")
  void otherTeacherDenied() throws Exception {
    teacherList("it.other@studymatch.com", "")
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
  }

  @Test
  @DisplayName("An edition that doesn't exist is 404")
  void unknownEdition() throws Exception {
    mockMvc.perform(get("/subject-editions/99999999/students").session(loginAs("it.teacher@studymatch.com")))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("A teacher discovers only their assigned editions, with subject and class metadata")
  void teacherListsOwnEditions() throws Exception {
    mockMvc.perform(get("/subject-editions/").session(loginAs("it.teacher@studymatch.com")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].id").value(edition.getId()))
        .andExpect(jsonPath("$[0].academicYear").value(2091))
        .andExpect(jsonPath("$[0].subject.code").value("ITVIEW1"))
        .andExpect(jsonPath("$[0].classSections[0]").value("T1"))
        .andExpect(jsonPath("$[0].classSections[1]").value("T2"));
  }

  @Test
  @DisplayName("An unrelated teacher cannot discover another teacher's edition")
  void unrelatedTeacherHasNoEditions() throws Exception {
    mockMvc.perform(get("/subject-editions/").session(loginAs("it.other@studymatch.com")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
  }

  @Test
  @DisplayName("Staff and unauthenticated users cannot use the teacher edition list")
  void editionListOnlyForTeachers() throws Exception {
    mockMvc.perform(get("/subject-editions/").session(loginAs("it.staff@studymatch.com")))
        .andExpect(status().isForbidden());
    mockMvc.perform(get("/subject-editions/")).andExpect(status().isUnauthorized());
  }

}
