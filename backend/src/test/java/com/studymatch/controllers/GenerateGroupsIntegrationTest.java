package com.studymatch.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.hamcrest.Matchers;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.studymatch.enums.AssignmentType;
import com.studymatch.enums.GroupStatus;
import com.studymatch.enums.Language;
import com.studymatch.enums.NotificationTarget;
import com.studymatch.enums.NotificationType;
import com.studymatch.enums.StatuteType;
import com.studymatch.models.Absence;
import com.studymatch.models.AcademicYear;
import com.studymatch.models.Assignment;
import com.studymatch.models.Category;
import com.studymatch.models.ClassSection;
import com.studymatch.models.Degree;
import com.studymatch.models.Enrolment;
import com.studymatch.models.Group;
import com.studymatch.models.GroupingRule;
import com.studymatch.models.Lesson;
import com.studymatch.models.Notification;
import com.studymatch.models.PersonalityProfile;
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
import com.studymatch.repositories.NotificationRepository;
import com.studymatch.repositories.StudentRepository;
import com.studymatch.repositories.SubjectEditionRepository;
import com.studymatch.repositories.SubjectRepository;
import com.studymatch.repositories.TeacherRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class GenerateGroupsIntegrationTest {

  private static final String PASSWORD = "Test1234";
  private static final String TEACHER = "it.groups.teacher@studymatch.com";
  private static final String OTHER_TEACHER = "it.groups.other@studymatch.com";

  @Autowired private MockMvc mockMvc;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private TeacherRepository teacherRepository;
  @Autowired private DegreeRepository degreeRepository;
  @Autowired private CategoryRepository categoryRepository;
  @Autowired private SubjectRepository subjectRepository;
  @Autowired private AcademicYearRepository academicYearRepository;
  @Autowired private SubjectEditionRepository subjectEditionRepository;
  @Autowired private StudentRepository studentRepository;
  @Autowired private EnrolmentRepository enrolmentRepository;
  @Autowired private AssignmentRepository assignmentRepository;
  @Autowired private GroupRepository groupRepository;
  @Autowired private LessonRepository lessonRepository;
  @Autowired private AbsenceRepository absenceRepository;
  @Autowired private NotificationRepository notificationRepository;

  private SubjectEdition edition;
  private List<Student> students;
  private Student blocked;

  // eight students who can be grouped (two without a test yet) and one blocked
  @BeforeEach
  void setUp() {
    Teacher teacher = teacherRepository.save(withoutPasswordChange(new Teacher("it.groups.teacher@example.com",
        TEACHER, "Test Teacher", passwordEncoder.encode(PASSWORD))));
    teacherRepository.save(withoutPasswordChange(new Teacher("it.groups.other@example.com", OTHER_TEACHER,
        "Other Teacher", passwordEncoder.encode(PASSWORD))));

    Degree degree = degreeRepository.save(new Degree("IT Groups Degree", 3));
    Category category = categoryRepository.save(new Category("IT Groups Category"));
    Subject subject = subjectRepository.save(new Subject("ITGROUPS1", "IT Groups Subject", 6, category));
    AcademicYear year = academicYearRepository.save(new AcademicYear(2094));
    edition = new SubjectEdition(subject, year, Set.of(teacher));
    edition.addClassSection("T1");
    edition = subjectEditionRepository.save(edition);

    students = new ArrayList<>();
    for (int i = 0; i < 8; i++) {
      Student student = new Student("it.groups." + i + "@example.com", "Student " + i, "hash", degree, 1);
      student.setLanguages(Set.of(Language.PORTUGUESE));
      if (i < 6) {
        student.setPersonalityProfile(new PersonalityProfile(0.1 * i, 0.5, 0.9 - 0.1 * i, 0.4, 0.5));
      }
      students.add(enrol(studentRepository.save(student)));
    }
    Student blockedStudent = new Student("it.groups.blocked@example.com", "Blocked", "hash", degree, 1);
    blockedStudent.block("test");
    blocked = enrol(studentRepository.save(blockedStudent));
  }

  private Student enrol(Student student) {
    enrolmentRepository.save(new Enrolment(student, edition.getClassSections().iterator().next()));
    return student;
  }

  private static <T extends User> T withoutPasswordChange(T user) {
    user.setMustChangePassword(false);
    return user;
  }

  private Assignment project(int minSize, int optimalSize, int maxSize) {
    return assignmentRepository.save(new Assignment(edition, "Project " + minSize + optimalSize + maxSize,
        AssignmentType.PROJECT, LocalDate.of(2095, 1, 15), new BigDecimal("0.40"),
        new GroupingRule(minSize, optimalSize, maxSize, false)));
  }

  private MockHttpSession loginAs(String email) throws Exception {
    MvcResult result = mockMvc.perform(post("/users/login").with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
        .andReturn();
    assertEquals(200, result.getResponse().getStatus());
    return (MockHttpSession) result.getRequest().getSession(false);
  }

  private String groupsUrl(Assignment assignment) {
    return "/subject-editions/" + edition.getId() + "/assignments/" + assignment.getId() + "/groups";
  }

  private ResultActions generate(String email, Assignment assignment, boolean replacePublished) throws Exception {
    return mockMvc.perform(post(groupsUrl(assignment)).with(csrf()).session(loginAs(email))
        .param("replacePublished", String.valueOf(replacePublished)));
  }

  private ResultActions generate(Assignment assignment) throws Exception {
    return generate(TEACHER, assignment, false);
  }

  private ResultActions publish(Assignment assignment) throws Exception {
    return mockMvc.perform(post(groupsUrl(assignment) + "/publish").with(csrf()).session(loginAs(TEACHER)));
  }

  private ResultActions discard(Assignment assignment) throws Exception {
    return mockMvc.perform(delete(groupsUrl(assignment) + "/draft").with(csrf()).session(loginAs(TEACHER)));
  }

  private List<Group> groups(Assignment assignment, GroupStatus status) {
    return groupRepository.findByAssignmentAndStatusOrderByNumber(assignment, status);
  }

  private List<Notification> notificationsOf(User user) {
    return notificationRepository.findByRecipientNumber(user.getNumber(), Pageable.unpaged()).getContent();
  }

  @Test
  @DisplayName("The teacher generates groups: 201 with two groups of 4, the blocked student left out, saved as drafts")
  void teacherGeneratesDraftGroups() throws Exception {
    // Arrange
    Assignment assignment = project(3, 4, 5);

    // Act
    generate(assignment)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[0].number").value(1))
        .andExpect(jsonPath("$[0].id").isNumber())
        .andExpect(jsonPath("$[0].status").value("DRAFT"))
        .andExpect(jsonPath("$[0].size").value(4))
        .andExpect(jsonPath("$[1].size").value(4))
        .andExpect(jsonPath("$[0].sharedLanguages[0]").value("PORTUGUESE"))
        .andExpect(jsonPath("$[*].members[*].number", Matchers.not(Matchers.hasItem(blocked.getNumber().intValue()))));

    // Assert - every student who can be grouped is in exactly one draft group
    List<Group> drafts = groups(assignment, GroupStatus.DRAFT);
    assertEquals(2, drafts.size());
    List<Long> placed = drafts.stream().flatMap(group -> group.getMembers().stream()).map(Student::getNumber)
        .sorted().toList();
    assertEquals(students.stream().map(Student::getNumber).sorted().toList(), placed);
  }

  @Test
  @DisplayName("With grades, attendance, a statute and a separation from a blocked student, everyone is still placed")
  void groupsWithEvidence() throws Exception {
    // Arrange - three finished the category before (18, 12, 6), one only finished another category (15)
    finishEarlierSubject(edition.getSubject().getCategory(), "ITGROUPS0", Map.of(0, 18, 1, 12, 2, 6));
    finishEarlierSubject(categoryRepository.save(new Category("IT Groups Other")), "ITGROUPSX", Map.of(3, 15));
    // one lesson held, student 4 missed it; student 5 has a statute, so no attendance tier
    ClassSection classSection = edition.getClassSections().iterator().next();
    Lesson lesson = lessonRepository.save(new Lesson(classSection, LocalDateTime.now().minusDays(1)));
    absenceRepository.save(new Absence(lesson,
        enrolmentRepository.findBySubjectEditionIdAndStudent_Number(edition.getId(), students.get(4).getNumber())
            .orElseThrow(), false));
    students.get(5).assignStatute(StatuteType.WORKING_STUDENT, LocalDate.now().plusYears(1));
    // a separation from the blocked student no longer matters
    Assignment assignment = project(3, 4, 5);
    assignment.getGroupingRule().addSeparation(students.getFirst(), blocked);
    assignmentRepository.save(assignment);

    // Act + Assert
    // grades only steer the matching, the preview never shows them
    // the statute student only shows that attendance doesn't apply (GR7)
    generate(assignment)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$[0].members[0].level").doesNotExist())
        .andExpect(jsonPath("$[*].members[?(@.number == " + students.get(5).getNumber() + ")].attendanceApplicable",
            Matchers.contains(false)))
        .andExpect(jsonPath("$[*].members[?(@.number == " + students.get(4).getNumber() + ")].attendanceApplicable",
            Matchers.contains(true)))
        .andExpect(jsonPath("$[0].members[0].attendance").doesNotExist());
    assertEquals(8, groups(assignment, GroupStatus.DRAFT).stream().mapToInt(group -> group.getMembers().size())
        .sum());
  }

  // an edition of an earlier year where the given students (by position) got these final grades
  private void finishEarlierSubject(Category category, String code, Map<Integer, Integer> finalGrades) {
    Subject subject = subjectRepository.save(new Subject(code, "IT Groups " + code, 6, category));
    SubjectEdition earlier = new SubjectEdition(subject,
        academicYearRepository.findById(2094).orElseThrow(), edition.getTeachers());
    earlier.addClassSection("T1");
    earlier = subjectEditionRepository.save(earlier);
    for (Map.Entry<Integer, Integer> grade : finalGrades.entrySet()) {
      Enrolment enrolment = new Enrolment(students.get(grade.getKey()), earlier.getClassSections().iterator().next());
      enrolment.setFinalGrade(grade.getValue());
      enrolmentRepository.save(enrolment);
    }
  }

  @Test
  @DisplayName("Regenerating replaces the drafts instead of adding more")
  void regenerateReplacesDrafts() throws Exception {
    // Arrange
    Assignment assignment = project(3, 4, 5);
    generate(assignment).andExpect(status().isCreated());

    // Act
    generate(assignment).andExpect(status().isCreated());

    // Assert
    assertEquals(2, groups(assignment, GroupStatus.DRAFT).size());
  }

  @Test
  @DisplayName("Confirming publishes the drafts")
  void confirmPublishesDrafts() throws Exception {
    // Arrange
    Assignment assignment = project(3, 4, 5);
    generate(assignment).andExpect(status().isCreated());

    // Act
    publish(assignment).andExpect(status().isNoContent());

    // Assert
    assertTrue(groups(assignment, GroupStatus.DRAFT).isEmpty());
    assertEquals(2, groups(assignment, GroupStatus.PUBLISHED).size());
  }

  @Test
  @DisplayName("With published groups, regenerating needs replacePublished, and they're replaced only on confirm")
  void regenerateAfterPublishing() throws Exception {
    // Arrange
    Assignment assignment = project(3, 4, 5);
    generate(assignment).andExpect(status().isCreated());
    publish(assignment).andExpect(status().isNoContent());
    List<Long> published = groups(assignment, GroupStatus.PUBLISHED).stream().map(Group::getId).toList();

    // Act + Assert - refused without the confirmation
    generate(assignment)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("GROUPS_ALREADY_PUBLISHED"));

    // Act + Assert - with it, the published groups stay until the new ones are confirmed
    generate(TEACHER, assignment, true).andExpect(status().isCreated());
    assertEquals(published, groups(assignment, GroupStatus.PUBLISHED).stream().map(Group::getId).toList());
    publish(assignment).andExpect(status().isNoContent());
    List<Group> now = groups(assignment, GroupStatus.PUBLISHED);
    assertEquals(2, now.size());
    assertTrue(now.stream().map(Group::getId).noneMatch(published::contains));
  }

  @Test
  @DisplayName("Cancelling removes the drafts and leaves the published groups alone")
  void cancelRemovesOnlyDrafts() throws Exception {
    // Arrange
    Assignment assignment = project(3, 4, 5);
    generate(assignment).andExpect(status().isCreated());
    publish(assignment).andExpect(status().isNoContent());
    generate(TEACHER, assignment, true).andExpect(status().isCreated());

    // Act
    discard(assignment).andExpect(status().isNoContent());

    // Assert
    assertTrue(groups(assignment, GroupStatus.DRAFT).isEmpty());
    assertEquals(2, groups(assignment, GroupStatus.PUBLISHED).size());
  }

  @Test
  @DisplayName("Confirming tells every member their group, and generating or cancelling tells nobody")
  void confirmNotifiesMembers() throws Exception {
    // Arrange
    Assignment assignment = project(3, 4, 5);
    generate(assignment).andExpect(status().isCreated());
    assertTrue(students.stream().allMatch(student -> notificationsOf(student).isEmpty()));

    // Act
    publish(assignment).andExpect(status().isNoContent());

    // Assert
    for (Group group : groups(assignment, GroupStatus.PUBLISHED)) {
      for (Student member : group.getMembers()) {
        List<Notification> notifications = notificationsOf(member);
        assertEquals(1, notifications.size());
        Notification notification = notifications.getFirst();
        assertEquals(NotificationType.GROUP_PUBLISHED, notification.getType());
        assertEquals(NotificationTarget.GROUP, notification.getTarget());
        assertEquals(group.getId(), notification.getTargetId());
        assertEquals(assignment.getName(), notification.getParams().get("assignment"));
        assertEquals(group.getNumber(), notification.getParams().get("number"));
      }
    }
    assertTrue(notificationsOf(blocked).isEmpty());

    // Act + Assert - new drafts and cancelling them change nothing for the students
    generate(TEACHER, assignment, true).andExpect(status().isCreated());
    discard(assignment).andExpect(status().isNoContent());
    assertTrue(students.stream().allMatch(student -> notificationsOf(student).size() == 1));
  }

  @Test
  @DisplayName("Confirming without drafts is 409 NO_DRAFT_GROUPS")
  void confirmWithoutDrafts() throws Exception {
    publish(project(3, 4, 5))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("NO_DRAFT_GROUPS"));
  }

  private ResultActions plan(Assignment assignment) throws Exception {
    return mockMvc.perform(get(groupsUrl(assignment) + "/generation").session(loginAs(TEACHER)));
  }

  @Test
  @DisplayName("Before generating: 8 students in 2 groups of 4, the blocked one left out, searching the whole 200 ms")
  void planBeforeGenerating() throws Exception {
    plan(project(3, 4, 5))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.students").value(8))
        .andExpect(jsonPath("$.groups").value(2))
        .andExpect(jsonPath("$.optimalSize").value(4))
        .andExpect(jsonPath("$.searchMillis").value(200));
  }

  @Test
  @DisplayName("Everyone in one group: nothing to search, 0 ms")
  void planOneGroup() throws Exception {
    plan(project(5, 8, 9))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.groups").value(1))
        .andExpect(jsonPath("$.searchMillis").value(0));
  }

  @Test
  @DisplayName("A class that can't be split plans no groups, generating says why")
  void planWithoutValidSplit() throws Exception {
    plan(project(5, 5, 5))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.groups").value(0))
        .andExpect(jsonPath("$.searchMillis").value(0));
  }

  @Test
  @DisplayName("Planning an exam is 400 ASSIGNMENT_NOT_GROUP_WORK")
  void planExam() throws Exception {
    Assignment exam = assignmentRepository.save(new Assignment(edition, "Exam", AssignmentType.EXAM,
        LocalDate.of(2095, 1, 20), new BigDecimal("0.60"), null));

    plan(exam)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("ASSIGNMENT_NOT_GROUP_WORK"));
  }

  @Test
  @DisplayName("An exam is 400 ASSIGNMENT_NOT_GROUP_WORK")
  void examHasNoGroups() throws Exception {
    // Arrange
    Assignment exam = assignmentRepository.save(new Assignment(edition, "Exam", AssignmentType.EXAM,
        LocalDate.of(2095, 1, 20), new BigDecimal("0.60"), null));

    // Act + Assert
    generate(exam)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("ASSIGNMENT_NOT_GROUP_WORK"));
  }

  @Test
  @DisplayName("8 students with a minimum of 9 is 409 NOT_ENOUGH_STUDENTS_FOR_GROUPS, the blocked one doesn't count")
  void fewerStudentsThanMinimum() throws Exception {
    generate(project(9, 9, 9))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("NOT_ENOUGH_STUDENTS_FOR_GROUPS"));
  }

  @Test
  @DisplayName("8 students in groups of exactly 5 is 409 NO_VALID_GROUP_SPLIT")
  void noValidSplit() throws Exception {
    generate(project(5, 5, 5))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("NO_VALID_GROUP_SPLIT"));
  }

  @Test
  @DisplayName("Separations that leave no valid grouping are 409 SEPARATIONS_CANNOT_BE_MET")
  void separationsCannotBeMet() throws Exception {
    // Arrange - two groups of 4, and the first student is kept away from five of the other seven
    Assignment assignment = project(4, 4, 4);
    for (int i = 1; i <= 5; i++) {
      assignment.getGroupingRule().addSeparation(students.getFirst(), students.get(i));
    }
    assignmentRepository.save(assignment);

    // Act + Assert
    generate(assignment)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("SEPARATIONS_CANNOT_BE_MET"));
  }

  @Test
  @DisplayName("A teacher of another edition is 403")
  void otherTeacherIsDenied() throws Exception {
    generate(OTHER_TEACHER, project(3, 4, 5), false)
        .andExpect(status().isForbidden());
  }

}
