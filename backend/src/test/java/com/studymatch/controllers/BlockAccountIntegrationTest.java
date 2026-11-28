package com.studymatch.controllers;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
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
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.studymatch.enums.AccountStatus;
import com.studymatch.enums.NotificationType;
import com.studymatch.enums.StudyRoomStatus;
import com.studymatch.enums.TutorApplicationStatus;
import com.studymatch.models.AcademicYear;
import com.studymatch.models.Admin;
import com.studymatch.models.Category;
import com.studymatch.models.Degree;
import com.studymatch.models.Notification;
import com.studymatch.models.PersonalityProfile;
import com.studymatch.models.Staff;
import com.studymatch.models.Student;
import com.studymatch.models.StudyRoom;
import com.studymatch.models.Subject;
import com.studymatch.models.SubjectEdition;
import com.studymatch.models.Teacher;
import com.studymatch.models.TutorApplication;
import com.studymatch.models.TutorRole;
import com.studymatch.models.User;
import com.studymatch.repositories.AcademicYearRepository;
import com.studymatch.repositories.AdminRepository;
import com.studymatch.repositories.CategoryRepository;
import com.studymatch.repositories.DegreeRepository;
import com.studymatch.repositories.NotificationRepository;
import com.studymatch.repositories.StaffRepository;
import com.studymatch.repositories.StudentRepository;
import com.studymatch.repositories.StudyRoomRepository;
import com.studymatch.repositories.SubjectEditionRepository;
import com.studymatch.repositories.SubjectRepository;
import com.studymatch.repositories.TeacherRepository;
import com.studymatch.repositories.TutorApplicationRepository;
import com.studymatch.repositories.TutorRoleRepository;
import com.studymatch.repositories.UserRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class BlockAccountIntegrationTest {

  private static final String PASSWORD = "Test1234";
  private static final String LINK = "https://meet.google.com/abc-defg-hij";
  private static final String REASON = "{\"reason\": \"test reason\"}";

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private PasswordEncoder passwordEncoder;

  @Autowired
  private UserRepository userRepository;

  @Autowired
  private AdminRepository adminRepository;

  @Autowired
  private StaffRepository staffRepository;

  @Autowired
  private TeacherRepository teacherRepository;

  @Autowired
  private StudentRepository studentRepository;

  @Autowired
  private DegreeRepository degreeRepository;

  @Autowired
  private CategoryRepository categoryRepository;

  @Autowired
  private SubjectRepository subjectRepository;

  @Autowired
  private AcademicYearRepository academicYearRepository;

  @Autowired
  private SubjectEditionRepository subjectEditionRepository;

  @Autowired
  private TutorApplicationRepository tutorApplicationRepository;

  @Autowired
  private TutorRoleRepository tutorRoleRepository;

  @Autowired
  private StudyRoomRepository studyRoomRepository;

  @Autowired
  private NotificationRepository notificationRepository;

  private Admin admin;
  private Staff staff;
  private Teacher teacher;
  private Student tutor;
  private StudyRoom upcomingRoom;
  private StudyRoom startedRoom;
  private TutorApplication pendingApplication;
  private MockHttpSession adminSession;

  // a tutor with an upcoming room, a room in progress and a pending application (rolled back after each test)
  @BeforeEach
  void setUp() throws Exception {
    admin = adminRepository.save(withoutPasswordChange(new Admin("it.block.admin@example.com",
        "it.block.admin@studymatch.com", "Block Test Admin", passwordEncoder.encode(PASSWORD))));
    staff = staffRepository.save(withoutPasswordChange(new Staff("it.block.staff@example.com",
        "it.block.staff@studymatch.com", "Block Test Staff", passwordEncoder.encode(PASSWORD))));
    teacher = teacherRepository.save(withoutPasswordChange(new Teacher("it.block.teacher@example.com",
        "it.block.teacher@studymatch.com", "Block Test Teacher", passwordEncoder.encode(PASSWORD))));

    Category category = categoryRepository.save(new Category("IT Block Category"));
    Subject maths = subjectRepository.save(new Subject("IT-BLOCK-MATH", "Block Test Maths", 6, category));
    Subject physics = subjectRepository.save(new Subject("IT-BLOCK-PHYS", "Block Test Physics", 6, category));

    tutor = saveStudent();
    TutorRole mathsRole = tutorRoleRepository.save(
        tutorApplicationRepository.save(new TutorApplication(tutor, maths, "test motivation")).approve(teacher));
    pendingApplication = tutorApplicationRepository.save(new TutorApplication(tutor, physics, "test motivation"));

    upcomingRoom = studyRoomRepository.save(new StudyRoom(mathsRole, tomorrowAt(14), tomorrowAt(16), 5,
        "test room", LINK));
    startedRoom = new StudyRoom(mathsRole, tomorrowAt(18), tomorrowAt(19), 5, "test room", LINK);
    ReflectionTestUtils.setField(startedRoom, "startsAt", LocalDateTime.now().minusMinutes(30));
    ReflectionTestUtils.setField(startedRoom, "endsAt", LocalDateTime.now().plusMinutes(30));
    startedRoom = studyRoomRepository.save(startedRoom);

    adminSession = loginAndGetSession(admin.getInstitutionalEmail());
  }

  private static <T extends User> T withoutPasswordChange(T user) {
    user.setMustChangePassword(false);
    return user;
  }

  private Student saveStudent() {
    Degree degree = degreeRepository.save(new Degree("IT Block Degree", 3));
    Student student = new Student("it.block.tutor@example.com", "Block Test Tutor", passwordEncoder.encode(PASSWORD),
        degree, 1);
    student.setMustChangePassword(false);
    student.setPersonalityProfile(new PersonalityProfile(0.5, 0.5, 0.5, 0.5, 0.5));
    student = studentRepository.save(student);
    student.generateInstitutionalEmail("studymatch.com");
    return studentRepository.saveAndFlush(student);
  }

  private ResultActions login(String email) throws Exception {
    return mockMvc.perform(post("/users/login").with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"));
  }

  private MockHttpSession loginAndGetSession(String email) throws Exception {
    MvcResult result = login(email).andReturn();
    assertEquals(200, result.getResponse().getStatus());
    return (MockHttpSession) result.getRequest().getSession(false);
  }

  private ResultActions block(Long number, MockHttpSession session, String body) throws Exception {
    return mockMvc.perform(put("/users/{number}/block", number).session(session).with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content(body));
  }

  private ResultActions unblock(Long number, MockHttpSession session) throws Exception {
    return mockMvc.perform(put("/users/{number}/unblock", number).session(session).with(csrf()));
  }

  private static LocalDateTime tomorrowAt(int hour) {
    return LocalDateTime.now().plusDays(1).truncatedTo(ChronoUnit.DAYS).withHour(hour);
  }

  // account list

  @Test
  @DisplayName("Searching by part of the name finds the account, of any role")
  void searchByName() throws Exception {
    // Act + Assert
    mockMvc.perform(get("/users/").session(adminSession).param("search", "block test"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[*].number").value(hasItem(tutor.getNumber().intValue())))
        .andExpect(jsonPath("$.content[*].number").value(hasItem(teacher.getNumber().intValue())))
        .andExpect(jsonPath("$.content[*].number").value(hasItem(admin.getNumber().intValue())));
  }

  @Test
  @DisplayName("Searching by part of the institutional email finds the account")
  void searchByInstitutionalEmail() throws Exception {
    // Act + Assert
    mockMvc.perform(get("/users/").session(adminSession).param("search", "it.block.staff@"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].role").value("STAFF"))
        .andExpect(jsonPath("$.content[0].accountStatus").value("ACTIVE"));
  }

  @Test
  @DisplayName("Searching by the exact number finds that one account")
  void searchByNumber() throws Exception {
    // Act + Assert
    mockMvc.perform(get("/users/").session(adminSession).param("search", tutor.getNumber().toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[*].number").value(hasItem(tutor.getNumber().intValue())))
        .andExpect(jsonPath("$.content[0].institutionalEmail").exists());
  }

  @Test
  @DisplayName("The status filter leaves out the accounts that don't match")
  void filterByStatus() throws Exception {
    // Act + Assert
    mockMvc.perform(get("/users/").session(adminSession).param("search", "block test").param("status", "BLOCKED"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[*].number").value(not(hasItem(tutor.getNumber().intValue()))));
  }

  @Test
  @DisplayName("Without a search only the status filter is used")
  void listWithoutSearch() throws Exception {
    // Arrange
    block(staff.getNumber(), adminSession, REASON).andExpect(status().isOk());

    // Act + Assert
    mockMvc.perform(get("/users/").session(adminSession).param("status", "BLOCKED"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[*].number").value(hasItem(staff.getNumber().intValue())))
        .andExpect(jsonPath("$.content[*].accountStatus").value(not(hasItem("ACTIVE"))));
  }

  @Test
  @DisplayName("A search of only spaces is ignored, like no search at all")
  void blankSearchIsIgnored() throws Exception {
    // Arrange
    block(staff.getNumber(), adminSession, REASON).andExpect(status().isOk());

    // Act + Assert
    mockMvc.perform(get("/users/").session(adminSession).param("search", "   ").param("status", "BLOCKED"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[*].number").value(hasItem(staff.getNumber().intValue())));
  }

  @Test
  @DisplayName("A number too long to be an account number is searched as text instead of crashing")
  void hugeNumberIsSearchedAsText() throws Exception {
    // Act + Assert
    mockMvc.perform(get("/users/").session(adminSession).param("search", "9".repeat(19)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(0));
  }

  @Test
  @DisplayName("Only admins can list the accounts")
  void listNeedsAdmin() throws Exception {
    // Arrange
    MockHttpSession staffSession = loginAndGetSession(staff.getInstitutionalEmail());

    // Act + Assert
    mockMvc.perform(get("/users/").session(staffSession))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
  }

  @Test
  @DisplayName("Listing the accounts needs a login")
  void listNeedsLogin() throws Exception {
    // Act + Assert
    mockMvc.perform(get("/users/"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
  }

  // impact

  @Test
  @DisplayName("The impact counts the room that hasn't started and the pending application")
  void impactOfTutor() throws Exception {
    // Act + Assert
    mockMvc.perform(get("/users/{number}/block-impact", tutor.getNumber()).session(adminSession))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.account.number").value(tutor.getNumber()))
        .andExpect(jsonPath("$.upcomingStudyRooms").value(1))
        .andExpect(jsonPath("$.pendingTutorApplications").value(1))
        .andExpect(jsonPath("$.currentEditions").isEmpty());
  }

  @Test
  @DisplayName("A teacher's impact lists their editions of the current year")
  void impactOfTeacher() throws Exception {
    // Arrange - the newest year is the current one
    Subject subject = subjectRepository.save(new Subject("IT-BLOCK-SQ", "Block Test Quality", 6,
        categoryRepository.save(new Category("IT Block Teacher Category"))));
    subjectEditionRepository.save(new SubjectEdition(subject, academicYearRepository.save(new AcademicYear(2098)),
        Set.of(teacher)));

    // Act + Assert
    mockMvc.perform(get("/users/{number}/block-impact", teacher.getNumber()).session(adminSession))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.currentEditions[0].subject.name").value("Block Test Quality"))
        .andExpect(jsonPath("$.upcomingStudyRooms").value(0));
  }

  @Test
  @DisplayName("The impact of an admin account is refused")
  void impactOfAdmin() throws Exception {
    // Act + Assert
    mockMvc.perform(get("/users/{number}/block-impact", admin.getNumber()).session(adminSession))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("ADMIN_CANNOT_BE_BLOCKED"));
  }

  @Test
  @DisplayName("The impact of an unknown account is NOT_FOUND")
  void impactOfUnknownAccount() throws Exception {
    // Act + Assert
    mockMvc.perform(get("/users/{number}/block-impact", 9_999_999L).session(adminSession))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
  }

  // block

  @Test
  @DisplayName("Blocking a tutor saves the reason and cancels only what hasn't started")
  void blockTutor() throws Exception {
    // Act
    block(tutor.getNumber(), adminSession, REASON)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.account.accountStatus").value("BLOCKED"))
        .andExpect(jsonPath("$.cancelledStudyRooms").value(1))
        .andExpect(jsonPath("$.cancelledTutorApplications").value(1));

    // Assert
    var saved = userRepository.findById(tutor.getNumber()).orElseThrow();
    assertEquals(AccountStatus.BLOCKED, saved.getAccountStatus());
    assertEquals("test reason", saved.getBlockReason());
    assertEquals(StudyRoomStatus.CANCELLED, studyRoomRepository.findById(upcomingRoom.getId()).orElseThrow().getStatus());
    assertEquals(StudyRoomStatus.SCHEDULED, studyRoomRepository.findById(startedRoom.getId()).orElseThrow().getStatus());
    assertEquals(TutorApplicationStatus.CANCELLED,
        tutorApplicationRepository.findById(pendingApplication.getId()).orElseThrow().getStatus());
  }

  @Test
  @DisplayName("A session opened before the block stops working, and the account can't log in again")
  void blockEndsSession() throws Exception {
    // Arrange
    MockHttpSession tutorSession = loginAndGetSession(tutor.getInstitutionalEmail());
    block(tutor.getNumber(), adminSession, REASON).andExpect(status().isOk());

    // Act + Assert - the next request ends the session, the one after it isn't logged in anymore
    mockMvc.perform(get("/users/me").session(tutorSession))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("ACCOUNT_BLOCKED"));
    mockMvc.perform(get("/users/me").session(tutorSession))
        .andExpect(status().isUnauthorized());
    login(tutor.getInstitutionalEmail())
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("ACCOUNT_BLOCKED"));
  }

  @Test
  @DisplayName("A teacher with editions this year can still be blocked, the warning doesn't stop it")
  void blockTeacher() throws Exception {
    // Arrange
    Subject subject = subjectRepository.save(new Subject("IT-BLOCK-SQ", "Block Test Quality", 6,
        categoryRepository.save(new Category("IT Block Teacher Category"))));
    subjectEditionRepository.save(new SubjectEdition(subject, academicYearRepository.save(new AcademicYear(2098)),
        Set.of(teacher)));

    // Act + Assert
    block(teacher.getNumber(), adminSession, REASON)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.account.accountStatus").value("BLOCKED"))
        .andExpect(jsonPath("$.cancelledStudyRooms").value(0));
  }

  @Test
  @DisplayName("A blank reason is a field error and nothing is blocked")
  void blankReasonIsRefused() throws Exception {
    // Act
    block(tutor.getNumber(), adminSession, "{\"reason\": \"   \"}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.fieldErrors[0].field").value("reason"))
        .andExpect(jsonPath("$.fieldErrors[0].code").value("NOT_BLANK"));

    // Assert
    assertEquals(AccountStatus.ACTIVE, userRepository.findById(tutor.getNumber()).orElseThrow().getAccountStatus());
  }

  @Test
  @DisplayName("An admin account can't be blocked")
  void blockAdminIsRefused() throws Exception {
    // Act + Assert
    block(admin.getNumber(), adminSession, REASON)
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("ADMIN_CANNOT_BE_BLOCKED"));
  }

  @Test
  @DisplayName("Blocking an account twice is a conflict")
  void blockTwiceIsRefused() throws Exception {
    // Arrange
    block(tutor.getNumber(), adminSession, REASON).andExpect(status().isOk());

    // Act + Assert
    block(tutor.getNumber(), adminSession, REASON)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("ACCOUNT_ALREADY_BLOCKED"));
  }

  @Test
  @DisplayName("Blocking an unknown account is NOT_FOUND")
  void blockUnknownAccount() throws Exception {
    // Act + Assert
    block(9_999_999L, adminSession, REASON)
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
  }

  @Test
  @DisplayName("Staff can't block accounts")
  void blockNeedsAdmin() throws Exception {
    // Arrange
    MockHttpSession staffSession = loginAndGetSession(staff.getInstitutionalEmail());

    // Act + Assert
    block(tutor.getNumber(), staffSession, REASON)
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
  }

  @Test
  @DisplayName("Blocking needs a login")
  void blockNeedsLogin() throws Exception {
    // Act + Assert
    mockMvc.perform(put("/users/{number}/block", tutor.getNumber()).with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content(REASON))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
  }

  @Test
  @DisplayName("Blocking without the CSRF token is refused")
  void blockNeedsCsrf() throws Exception {
    // Act + Assert
    mockMvc.perform(put("/users/{number}/block", tutor.getNumber()).session(adminSession)
        .contentType(MediaType.APPLICATION_JSON)
        .content(REASON))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("CSRF_TOKEN_INVALID"));
  }

  // unblock (#33)

  @Test
  @DisplayName("Unblocking lets the tutor log in again, the reason is cleared and the cancelled room stays cancelled")
  void unblockTutor() throws Exception {
    // Arrange
    block(tutor.getNumber(), adminSession, REASON).andExpect(status().isOk());

    // Act
    unblock(tutor.getNumber(), adminSession)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accountStatus").value("ACTIVE"));

    // Assert
    var saved = userRepository.findById(tutor.getNumber()).orElseThrow();
    assertEquals(AccountStatus.ACTIVE, saved.getAccountStatus());
    assertNull(saved.getBlockReason());
    assertEquals(StudyRoomStatus.CANCELLED, studyRoomRepository.findById(upcomingRoom.getId()).orElseThrow().getStatus());
    login(tutor.getInstitutionalEmail()).andExpect(status().isOk());
  }

  @Test
  @DisplayName("Unblocking an account that isn't blocked is a conflict")
  void unblockActiveAccountIsRefused() throws Exception {
    // Act + Assert
    unblock(tutor.getNumber(), adminSession)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_BLOCKED"));
  }

  @Test
  @DisplayName("Unblocking an unknown account is NOT_FOUND")
  void unblockUnknownAccount() throws Exception {
    // Act + Assert
    unblock(9_999_999L, adminSession)
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
  }

  @Test
  @DisplayName("Staff can't unblock accounts")
  void unblockNeedsAdmin() throws Exception {
    // Arrange
    block(tutor.getNumber(), adminSession, REASON).andExpect(status().isOk());
    MockHttpSession staffSession = loginAndGetSession(staff.getInstitutionalEmail());

    // Act + Assert
    unblock(tutor.getNumber(), staffSession)
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
  }

  private List<Notification> notificationsOf(User user) {
    return notificationRepository.findByRecipientNumber(user.getNumber(), Pageable.unpaged()).getContent();
  }

  @Test
  @DisplayName("Blocking a tutor tells the participants of the cancelled room, without the block reason")
  void blockingNotifiesParticipants() throws Exception {
    // Arrange
    Degree degree = degreeRepository.save(new Degree("IT Block Participant Degree", 3));
    Student ana = new Student("it.block.ana@example.com", "Block Test Ana", passwordEncoder.encode(PASSWORD), degree, 1);
    ana = studentRepository.save(ana);
    ana.generateInstitutionalEmail("studymatch.com");
    ana = studentRepository.saveAndFlush(ana);
    upcomingRoom.join(ana);
    upcomingRoom = studyRoomRepository.saveAndFlush(upcomingRoom);

    // Act
    block(tutor.getNumber(), adminSession, "{\"reason\":\"test reason\"}").andExpect(status().isOk());

    // Assert
    List<Notification> notifications = notificationsOf(ana);
    assertEquals(1, notifications.size());
    assertEquals(NotificationType.STUDYROOM_CANCELLED, notifications.getFirst().getType());
    assertEquals(upcomingRoom.getId(), notifications.getFirst().getTargetId());
    assertFalse(notifications.getFirst().getParams().containsKey("reason"));
  }

  @Test
  @DisplayName("Unblocking tells the user, who sees it after logging in again")
  void unblockingNotifiesUser() throws Exception {
    // Arrange
    block(tutor.getNumber(), adminSession, "{\"reason\":\"test reason\"}").andExpect(status().isOk());

    // Act
    unblock(tutor.getNumber(), adminSession).andExpect(status().isOk());

    // Assert
    mockMvc.perform(get("/notifications/").session(loginAndGetSession(tutor.getInstitutionalEmail())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].type").value("ACCOUNT_UNBLOCKED"))
        .andExpect(jsonPath("$.content[0].read").value(false));
    assertEquals(1, notificationsOf(tutor).size());
  }

}
