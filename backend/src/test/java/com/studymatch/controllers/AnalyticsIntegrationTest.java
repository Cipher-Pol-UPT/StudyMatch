package com.studymatch.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
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

import com.studymatch.enums.AssignmentType;
import com.studymatch.enums.StatuteType;
import com.studymatch.models.Absence;
import com.studymatch.models.AcademicYear;
import com.studymatch.models.Assignment;
import com.studymatch.models.Category;
import com.studymatch.models.Degree;
import com.studymatch.models.Enrolment;
import com.studymatch.models.Group;
import com.studymatch.models.GroupingRule;
import com.studymatch.models.Lesson;
import com.studymatch.models.PersonalityProfile;
import com.studymatch.models.Staff;
import com.studymatch.models.Student;
import com.studymatch.models.Subject;
import com.studymatch.models.SubjectEdition;
import com.studymatch.models.Teacher;
import com.studymatch.models.User;
import com.studymatch.repositories.AbsenceRepository;
import com.studymatch.repositories.AcademicYearRepository;
import com.studymatch.repositories.AssignmentRepository;
import com.studymatch.repositories.CategoryRepository;
import com.studymatch.repositories.DegreeRepository;
import com.studymatch.repositories.EnrolmentRepository;
import com.studymatch.repositories.GroupRepository;
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
class AnalyticsIntegrationTest {

  private static final String PASSWORD = "Test1234";
  private static final String STAFF = "it.analytics.staff@studymatch.com";
  private static final String TEACHER = "it.analytics.teacher@studymatch.com";
  private static final int YEAR = 2095;
  // lessons can't be in the future, and their dates don't have to match the year's number
  private static final int LESSONS = 2025;

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
  @Autowired private AssignmentRepository assignmentRepository;
  @Autowired private GroupRepository groupRepository;

  private SubjectEdition programming;
  private SubjectEdition maths;
  private Group published;

  // 2095 has Test Programming (T1, T2) and Test Maths (T1). ana is in her second year with a 16 from 2094,
  // rui has a statute, eva is a first year in T2 with no profile. ana and eva form the one published group
  @BeforeEach
  void setUp() {
    staffRepository.save(withoutPasswordChange(new Staff("it.analytics.staff@example.com", STAFF, "Test Staff",
        passwordEncoder.encode(PASSWORD))));
    Teacher teacher = teacherRepository.save(withoutPasswordChange(new Teacher("it.analytics.teacher@example.com",
        TEACHER, "Test Teacher", passwordEncoder.encode(PASSWORD))));
    Degree degree = degreeRepository.save(new Degree("IT Analytics Degree", 3));
    Category category = categoryRepository.save(new Category("IT Analytics Category"));
    Subject programmingSubject = subjectRepository.save(new Subject("ITANALYT1", "Test Programming", 6, category));
    Subject mathsSubject = subjectRepository.save(new Subject("ITANALYT2", "Test Maths", 6, category));

    SubjectEdition lastYear = edition(programmingSubject, academicYearRepository.save(new AcademicYear(YEAR - 1)),
        teacher, "T1");
    AcademicYear year = academicYearRepository.save(new AcademicYear(YEAR));
    programming = edition(programmingSubject, year, teacher, "T1", "T2");
    maths = edition(mathsSubject, year, teacher, "T1");

    Student ana = student("ana", degree, 2, new PersonalityProfile(0.2, 0.3, 0.9, 0.4, 0.1));
    Student rui = student("rui", degree, 2, new PersonalityProfile(0.8, 0.3, 0.2, 0.4, 0.1));
    rui.assignStatute(StatuteType.WORKING_STUDENT, LocalDate.now().plusMonths(6));
    Student eva = student("eva", degree, 1, null);

    Enrolment passed = new Enrolment(ana, lastYear.getClassSection("T1"));
    passed.setFinalGrade(16);
    enrolmentRepository.save(passed);
    Enrolment anaProgramming = enrolmentRepository.save(new Enrolment(ana, programming.getClassSection("T1")));
    enrolmentRepository.save(new Enrolment(ana, maths.getClassSection("T1")));
    Enrolment ruiProgramming = enrolmentRepository.save(new Enrolment(rui, programming.getClassSection("T1")));
    enrolmentRepository.save(new Enrolment(eva, programming.getClassSection("T2")));

    // T1 has a lesson in October and one in November, ana missed November; rui's absence doesn't count (statute)
    lessonRepository.save(new Lesson(programming.getClassSection("T1"), LocalDateTime.of(LESSONS, 10, 5, 9, 0)));
    Lesson november = lessonRepository.save(new Lesson(programming.getClassSection("T1"),
        LocalDateTime.of(LESSONS, 11, 9, 9, 0)));
    lessonRepository.save(new Lesson(programming.getClassSection("T2"), LocalDateTime.of(LESSONS, 10, 6, 9, 0)));
    absenceRepository.save(new Absence(november, anaProgramming, false));
    absenceRepository.save(new Absence(november, ruiProgramming, false));

    Assignment project = assignmentRepository.save(new Assignment(programming, "Test Project", AssignmentType.PROJECT,
        LocalDate.of(YEAR + 1, 1, 15), new BigDecimal("0.5"), new GroupingRule(2, 2, 3, false)));
    published = new Group(project, 1);
    published.addMember(ana);
    published.addMember(eva);
    published.publish();
    published = groupRepository.save(published);
    Group draft = new Group(project, 2);
    draft.addMember(rui);
    groupRepository.saveAndFlush(draft);
  }

  private SubjectEdition edition(Subject subject, AcademicYear year, Teacher teacher, String... classSections) {
    SubjectEdition edition = new SubjectEdition(subject, year, Set.of(teacher));
    for (String code : classSections) {
      edition.addClassSection(code);
    }
    return subjectEditionRepository.save(edition);
  }

  private Student student(String name, Degree degree, int curricularYear, PersonalityProfile profile) {
    Student student = new Student("it.analytics." + name + "@example.com", "Test " + name, "hash", degree,
        curricularYear);
    student.setPersonalityProfile(profile);
    return studentRepository.save(student);
  }

  private static <T extends User> T withoutPasswordChange(T user) {
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

  // params as name, value, name, value...
  private ResultActions analytics(String... params) throws Exception {
    var request = get("/analytics/").session(loginAs(STAFF));
    for (int i = 0; i < params.length; i += 2) {
      request.param(params[i], params[i + 1]);
    }
    return mockMvc.perform(request);
  }

  @Test
  @DisplayName("The whole year: indicators, attendance per month, strongest traits and the published group")
  void wholeYear() throws Exception {
    // Act + Assert - 3 lessons counted (rui has a statute), ana missed 1
    analytics("academicYear", String.valueOf(YEAR))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.academicYear").value(YEAR))
        .andExpect(jsonPath("$.totalStudents").value(3))
        .andExpect(jsonPath("$.activeSubjects").value(2))
        .andExpect(jsonPath("$.averageAttendance").value(Matchers.closeTo(66.67, 0.01)))
        .andExpect(jsonPath("$.attendanceOverTime.length()").value(2))
        .andExpect(jsonPath("$.attendanceOverTime[0].month").value(LESSONS + "-10"))
        .andExpect(jsonPath("$.attendanceOverTime[0].attendance").value(100.0))
        .andExpect(jsonPath("$.attendanceOverTime[1].month").value(LESSONS + "-11"))
        .andExpect(jsonPath("$.attendanceOverTime[1].attendance").value(0.0))
        .andExpect(jsonPath("$.personalityDistribution[*].trait").value(Matchers.contains("OPENNESS",
            "CONSCIENTIOUSNESS", "EXTRAVERSION", "AGREEABLENESS", "NEUROTICISM")))
        .andExpect(jsonPath("$.personalityDistribution[*].students").value(Matchers.contains(1, 0, 1, 0, 0)))
        .andExpect(jsonPath("$.groupBalance.length()").value(1))
        .andExpect(jsonPath("$.groupBalance[0].assignment").value("Test Project"))
        .andExpect(jsonPath("$.groupBalance[0].members").value(2))
        .andExpect(jsonPath("$.groupBalance[0].averageGrade").value(16.0))
        .andExpect(jsonPath("$.firstYearOnly").value(false));
  }

  @Test
  @DisplayName("The filters offer the years, the year's editions with their classes and the published groups")
  void filterOptions() throws Exception {
    analytics("academicYear", String.valueOf(YEAR))
        .andExpect(jsonPath("$.filters.academicYears").value(Matchers.hasItems(YEAR, YEAR - 1)))
        .andExpect(jsonPath("$.filters.subjectEditions[*].subjectName").value(Matchers.contains("Test Maths",
            "Test Programming")))
        .andExpect(jsonPath("$.filters.subjectEditions[1].classSections").value(Matchers.contains("T1", "T2")))
        .andExpect(jsonPath("$.filters.groups.length()").value(1))
        .andExpect(jsonPath("$.filters.groups[0].id").value(published.getId()));
  }

  @Test
  @DisplayName("Without a year it shows the current one, the newest registered")
  void defaultsToTheCurrentYear() throws Exception {
    analytics()
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.academicYear").value(YEAR))
        .andExpect(jsonPath("$.totalStudents").value(3));
  }

  @Test
  @DisplayName("A class of an edition only counts its own students, here only first years")
  void filterByClass() throws Exception {
    analytics("subjectEditionId", String.valueOf(programming.getId()), "classSection", "T2")
        .andExpect(jsonPath("$.totalStudents").value(1))
        .andExpect(jsonPath("$.activeSubjects").value(1))
        .andExpect(jsonPath("$.averageAttendance").value(100.0))
        .andExpect(jsonPath("$.firstYearOnly").value(true))
        .andExpect(jsonPath("$.groupBalance.length()").value(1));
  }

  @Test
  @DisplayName("A group counts its members in the group's subject only")
  void filterByGroup() throws Exception {
    analytics("groupId", String.valueOf(published.getId()))
        .andExpect(jsonPath("$.totalStudents").value(2))
        .andExpect(jsonPath("$.activeSubjects").value(1))
        .andExpect(jsonPath("$.attendanceOverTime.length()").value(2));
  }

  @Test
  @DisplayName("A selection with nobody in it gives empty analytics, not an error")
  void noData() throws Exception {
    analytics("subjectEditionId", String.valueOf(maths.getId()), "classSection", "T2")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalStudents").value(0))
        .andExpect(jsonPath("$.averageAttendance").doesNotExist())
        .andExpect(jsonPath("$.attendanceOverTime.length()").value(0))
        .andExpect(jsonPath("$.personalityDistribution[*].students").value(Matchers.contains(0, 0, 0, 0, 0)))
        .andExpect(jsonPath("$.groupBalance.length()").value(0))
        .andExpect(jsonPath("$.firstYearOnly").value(false));
  }

  @Test
  @DisplayName("A draft group isn't a filter, so asking for it gives empty analytics")
  void draftGroupIsNotMeasured() throws Exception {
    Long draftId = groupRepository.findAll().stream()
        .filter(group -> group.getNumber() == 2 && group.getAssignment().getName().equals("Test Project"))
        .findFirst().orElseThrow().getId();

    analytics("groupId", String.valueOf(draftId))
        .andExpect(jsonPath("$.totalStudents").value(0));
  }

  @Test
  @DisplayName("The statute type is never in the answer (GR7)")
  void statuteTypeIsNeverShown() throws Exception {
    String body = analytics("academicYear", String.valueOf(YEAR)).andReturn().getResponse().getContentAsString();

    assertFalse(body.contains("WORKING_STUDENT"));
    assertFalse(body.contains("statute"));
  }

  @Test
  @DisplayName("An academic year that isn't registered is NOT_FOUND")
  void unknownYear() throws Exception {
    analytics("academicYear", "1990")
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
  }

  @Test
  @DisplayName("Only Staff sees analytics, a teacher gets ACCESS_DENIED")
  void teacherIsDenied() throws Exception {
    mockMvc.perform(get("/analytics/").session(loginAs(TEACHER)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
  }

}
