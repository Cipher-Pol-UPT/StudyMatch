package com.studymatch.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
import com.studymatch.enums.NotificationTarget;
import com.studymatch.enums.NotificationType;
import com.studymatch.models.AcademicYear;
import com.studymatch.models.Assignment;
import com.studymatch.models.Category;
import com.studymatch.models.Degree;
import com.studymatch.models.Enrolment;
import com.studymatch.models.Group;
import com.studymatch.models.GroupingRule;
import com.studymatch.models.Notification;
import com.studymatch.models.Student;
import com.studymatch.models.Subject;
import com.studymatch.models.SubjectEdition;
import com.studymatch.models.Teacher;
import com.studymatch.models.User;
import com.studymatch.repositories.AcademicYearRepository;
import com.studymatch.repositories.AssignmentRepository;
import com.studymatch.repositories.CategoryRepository;
import com.studymatch.repositories.DegreeRepository;
import com.studymatch.repositories.EnrolmentRepository;
import com.studymatch.repositories.GroupRepository;
import com.studymatch.repositories.NotificationRepository;
import com.studymatch.repositories.StudentRepository;
import com.studymatch.repositories.SubjectEditionRepository;
import com.studymatch.repositories.SubjectRepository;
import com.studymatch.repositories.TeacherRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class EditGroupsIntegrationTest {

  private static final String PASSWORD = "Test1234";
  private static final String TEACHER = "it.editgroups.teacher@studymatch.com";
  private static final String OTHER_TEACHER = "it.editgroups.other@studymatch.com";

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
  @Autowired private NotificationRepository notificationRepository;

  private SubjectEdition edition;
  private Assignment assignment;
  private List<Student> students;
  private Student blocked;

  // six students who can be grouped and one blocked, and a project for groups of 2 to 3
  @BeforeEach
  void setUp() {
    Teacher teacher = teacherRepository.save(withoutPasswordChange(new Teacher("it.editgroups.teacher@example.com",
        TEACHER, "Test Teacher", passwordEncoder.encode(PASSWORD))));
    teacherRepository.save(withoutPasswordChange(new Teacher("it.editgroups.other@example.com", OTHER_TEACHER,
        "Other Teacher", passwordEncoder.encode(PASSWORD))));

    Degree degree = degreeRepository.save(new Degree("IT Edit Groups Degree", 3));
    Category category = categoryRepository.save(new Category("IT Edit Groups Category"));
    Subject subject = subjectRepository.save(new Subject("ITEDITGRP", "IT Edit Groups Subject", 6, category));
    AcademicYear year = academicYearRepository.save(new AcademicYear(2093));
    edition = new SubjectEdition(subject, year, Set.of(teacher));
    edition.addClassSection("T1");
    edition = subjectEditionRepository.save(edition);

    students = new ArrayList<>();
    for (int i = 0; i < 6; i++) {
      students.add(enrol(studentRepository.save(
          new Student("it.editgroups." + i + "@example.com", "Student " + i, "hash", degree, 1))));
    }
    Student blockedStudent = new Student("it.editgroups.blocked@example.com", "Blocked", "hash", degree, 1);
    blockedStudent.block("test");
    blocked = enrol(studentRepository.save(blockedStudent));

    assignment = assignmentRepository.save(new Assignment(edition, "Project", AssignmentType.PROJECT,
        LocalDate.of(2094, 1, 15), new BigDecimal("0.40"), new GroupingRule(2, 2, 3, false)));
  }

  private Student enrol(Student student) {
    enrolmentRepository.save(new Enrolment(student, edition.getClassSections().iterator().next()));
    return student;
  }

  private static <T extends User> T withoutPasswordChange(T user) {
    user.setMustChangePassword(false);
    return user;
  }

  // the assignment's groups of one status, numbered from 1, each with the students at these positions
  private List<Group> saveGroups(GroupStatus status, int[]... positions) {
    List<Group> groups = new ArrayList<>();
    for (int[] members : positions) {
      Group group = new Group(assignment, groups.size() + 1);
      for (int position : members) {
        group.addMember(students.get(position));
      }
      if (status == GroupStatus.PUBLISHED) {
        group.publish();
      }
      groups.add(groupRepository.save(group));
    }
    return groups;
  }

  private MockHttpSession loginAs(String email) throws Exception {
    MvcResult result = mockMvc.perform(post("/users/login").with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
        .andReturn();
    assertEquals(200, result.getResponse().getStatus());
    return (MockHttpSession) result.getRequest().getSession(false);
  }

  private ResultActions editAs(String email, String status, String... groups) throws Exception {
    return mockMvc.perform(put("/subject-editions/" + edition.getId() + "/assignments/" + assignment.getId()
        + "/groups/" + status).with(csrf()).session(loginAs(email))
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"groups\":[" + String.join(",", groups) + "]}"));
  }

  private ResultActions edit(String status, String... groups) throws Exception {
    return editAs(TEACHER, status, groups);
  }

  // one group of the request, an id of null is a new group
  private String group(Long id, Student... members) {
    return "{\"id\":" + id + ",\"members\":["
        + Stream.of(members).map(member -> member.getNumber().toString()).collect(Collectors.joining(",")) + "]}";
  }

  private Student student(int position) {
    return students.get(position);
  }

  private Set<Student> members(Group group) {
    return groupRepository.findById(group.getId()).orElseThrow().getMembers();
  }

  private List<Notification> notificationsOf(Student student) {
    return notificationRepository.findByRecipientNumber(student.getNumber(), Pageable.unpaged()).getContent();
  }

  // the group number each student was told, in the order of the students; null when they weren't told
  private List<Object> toldNumbers() {
    return students.stream().map(student -> {
      List<Notification> notifications = notificationsOf(student);
      if (notifications.isEmpty()) {
        return null;
      }
      Notification notification = notifications.getFirst();
      assertEquals(1, notifications.size());
      assertEquals(NotificationType.GROUP_UPDATED, notification.getType());
      assertEquals(NotificationTarget.ASSIGNMENT, notification.getTarget());
      assertEquals(assignment.getId(), notification.getTargetId());
      return notification.getParams().get("number");
    }).toList();
  }

  @Test
  @DisplayName("Editing published groups tells the members of a changed or new group, the rest and draft edits nobody")
  void editPublishedNotifiesChangedGroups() throws Exception {
    // Arrange
    List<Group> published = saveGroups(GroupStatus.PUBLISHED, new int[] { 0, 1 }, new int[] { 2, 3 },
        new int[] { 4, 5 });
    List<Group> drafts = saveGroups(GroupStatus.DRAFT, new int[] { 0, 1, 2 }, new int[] { 3, 4, 5 });
    edit("draft", group(drafts.get(0).getId(), student(0), student(1)),
        group(drafts.get(1).getId(), student(2), student(3), student(4), student(5)))
        .andExpect(status().isOk());

    // Act - group 1 stays, student 4 moves to group 2 and student 5 is alone in group 3
    edit("published", group(published.get(0).getId(), student(0), student(1)),
        group(published.get(1).getId(), student(2), student(3), student(4)),
        group(published.get(2).getId(), student(5)))
        .andExpect(status().isOk());

    // Assert
    assertEquals(Arrays.asList(null, null, 2, 2, 2, 3), toldNumbers());
    assertTrue(notificationsOf(blocked).isEmpty());
  }

  @Test
  @DisplayName("A published group that only moves down a number tells its members the new one")
  void renumberedGroupNotifiesMembers() throws Exception {
    // Arrange
    List<Group> published = saveGroups(GroupStatus.PUBLISHED, new int[] { 0, 1 }, new int[] { 2, 3 },
        new int[] { 4, 5 });

    // Act - group 2 goes, its students join group 1, and group 3 becomes group 2 with the same members
    edit("published", group(published.get(0).getId(), student(0), student(1), student(2), student(3)),
        group(published.get(2).getId(), student(4), student(5)))
        .andExpect(status().isOk());

    // Assert
    assertEquals(Arrays.asList(1, 1, 1, 1, 2, 2), toldNumbers());
  }

  @Test
  @DisplayName("Moving a student between draft groups: 200, the same two groups with the new members")
  void moveStudent() throws Exception {
    // Arrange
    List<Group> drafts = saveGroups(GroupStatus.DRAFT, new int[] { 0, 1, 2 }, new int[] { 3, 4, 5 });

    // Act
    edit("draft", group(drafts.get(0).getId(), student(0), student(1)),
        group(drafts.get(1).getId(), student(2), student(3), student(4), student(5)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[0].id").value(drafts.get(0).getId()))
        .andExpect(jsonPath("$[0].status").value("DRAFT"))
        .andExpect(jsonPath("$[0].size").value(2))
        .andExpect(jsonPath("$[1].size").value(4));

    // Assert - a size outside the range is only a warning on the page
    assertEquals(Set.of(student(0), student(1)), members(drafts.get(0)));
    assertEquals(Set.of(student(2), student(3), student(4), student(5)), members(drafts.get(1)));
  }

  @Test
  @DisplayName("Removing the middle group: the last one becomes group 2, keeping its id")
  void removeMiddleGroup() throws Exception {
    // Arrange
    List<Group> drafts = saveGroups(GroupStatus.DRAFT, new int[] { 0, 1 }, new int[] { 2, 3 }, new int[] { 4, 5 });

    // Act
    edit("draft", group(drafts.get(0).getId(), student(0), student(1), student(2)),
        group(drafts.get(2).getId(), student(3), student(4), student(5)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[1].id").value(drafts.get(2).getId()))
        .andExpect(jsonPath("$[1].number").value(2));

    // Assert
    List<Group> now = groupRepository.findByAssignmentAndStatusOrderByNumber(assignment, GroupStatus.DRAFT);
    assertEquals(List.of(drafts.get(0).getId(), drafts.get(2).getId()), now.stream().map(Group::getId).toList());
    assertEquals(List.of(1, 2), now.stream().map(Group::getNumber).toList());
  }

  @Test
  @DisplayName("Editing the published groups: a kept group keeps its id, a new one is published, the drafts stay")
  void editPublished() throws Exception {
    // Arrange
    List<Group> published = saveGroups(GroupStatus.PUBLISHED, new int[] { 0, 1, 2, 3, 4, 5 });
    List<Group> drafts = saveGroups(GroupStatus.DRAFT, new int[] { 0, 1, 2, 3, 4, 5 });

    // Act
    edit("published", group(published.getFirst().getId(), student(0), student(1), student(2)),
        group(null, student(3), student(4), student(5)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(published.getFirst().getId()))
        .andExpect(jsonPath("$[1].number").value(2))
        .andExpect(jsonPath("$[1].status").value("PUBLISHED"));

    // Assert
    assertEquals(2, groupRepository.findByAssignmentAndStatusOrderByNumber(assignment, GroupStatus.PUBLISHED).size());
    assertEquals(6, members(drafts.getFirst()).size());
  }

  @Test
  @DisplayName("A student left without a group is 400 STUDENT_WITHOUT_GROUP with their number, nothing changes")
  void studentLeftOut() throws Exception {
    // Arrange - the blocked student isn't in any group and doesn't have to be (GR5)
    List<Group> drafts = saveGroups(GroupStatus.DRAFT, new int[] { 0, 1, 2 }, new int[] { 3, 4, 5 });

    // Act
    edit("draft", group(drafts.get(0).getId(), student(0), student(1), student(2)),
        group(drafts.get(1).getId(), student(3), student(4)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("STUDENT_WITHOUT_GROUP"))
        .andExpect(jsonPath("$.params.numbers.length()").value(1))
        .andExpect(jsonPath("$.params.numbers[0]").value(student(5).getNumber()));

    // Assert
    assertEquals(3, members(drafts.get(1)).size());
    assertTrue(members(drafts.get(1)).stream().noneMatch(blocked::equals));
  }

  @Test
  @DisplayName("Adding a blocked student to a group is 400 BLOCKED_STUDENT_MOVED with their number (GR5)")
  void blockedStudentAdded() throws Exception {
    // Arrange
    List<Group> drafts = saveGroups(GroupStatus.DRAFT, new int[] { 0, 1, 2 }, new int[] { 3, 4, 5 });

    // Act
    edit("draft", group(drafts.get(0).getId(), student(0), student(1), student(2), blocked),
        group(drafts.get(1).getId(), student(3), student(4), student(5)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("BLOCKED_STUDENT_MOVED"))
        .andExpect(jsonPath("$.params.number").value(blocked.getNumber()));

    // Assert
    assertEquals(3, members(drafts.get(0)).size());
  }

  @Test
  @DisplayName("An empty group is 400 VALIDATION_FAILED")
  void emptyGroup() throws Exception {
    // Arrange
    List<Group> drafts = saveGroups(GroupStatus.DRAFT, new int[] { 0, 1, 2, 3, 4, 5 });

    // Act + Assert
    edit("draft", group(drafts.getFirst().getId(), student(0), student(1), student(2), student(3), student(4),
        student(5)), group(null))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
  }

  @Test
  @DisplayName("Editing published groups before any are published is 409 NO_PUBLISHED_GROUPS")
  void noPublishedGroups() throws Exception {
    // Arrange
    saveGroups(GroupStatus.DRAFT, new int[] { 0, 1, 2, 3, 4, 5 });

    // Act + Assert
    edit("published", group(null, student(0), student(1), student(2), student(3), student(4), student(5)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("NO_PUBLISHED_GROUPS"));
  }

  @Test
  @DisplayName("A teacher of another edition is 403")
  void otherTeacherIsDenied() throws Exception {
    // Arrange
    List<Group> drafts = saveGroups(GroupStatus.DRAFT, new int[] { 0, 1, 2, 3, 4, 5 });

    // Act + Assert
    editAs(OTHER_TEACHER, "draft", group(drafts.getFirst().getId(), student(0), student(1), student(2), student(3),
        student(4), student(5)))
        .andExpect(status().isForbidden());
  }

}
