package com.studymatch.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
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
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.studymatch.models.AcademicYear;
import com.studymatch.models.Category;
import com.studymatch.models.Degree;
import com.studymatch.models.Enrolment;
import com.studymatch.models.PersonalityProfile;
import com.studymatch.models.Student;
import com.studymatch.models.StudyRoom;
import com.studymatch.models.Subject;
import com.studymatch.models.SubjectEdition;
import com.studymatch.models.Teacher;
import com.studymatch.models.TutorApplication;
import com.studymatch.models.TutorRole;
import com.studymatch.repositories.AcademicYearRepository;
import com.studymatch.repositories.CategoryRepository;
import com.studymatch.repositories.DegreeRepository;
import com.studymatch.repositories.EnrolmentRepository;
import com.studymatch.repositories.StudentRepository;
import com.studymatch.repositories.StudyRoomRepository;
import com.studymatch.repositories.SubjectEditionRepository;
import com.studymatch.repositories.SubjectRepository;
import com.studymatch.repositories.TeacherRepository;
import com.studymatch.repositories.TutorApplicationRepository;
import com.studymatch.repositories.TutorRoleRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class JoinStudyRoomIntegrationTest {

  private static final String PASSWORD = "Test1234";
  private static final String LINK = "https://meet.google.com/abc-defg-hij";

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
  private StudyRoomRepository studyRoomRepository;

  private Teacher teacher;
  private Category category;
  private Subject maths;
  private SubjectEdition mathsEdition;
  private Student tutor;
  private Student enrolledStudent;
  private Student notEnrolledStudent;
  private Student passedStudent;
  private TutorRole mathsRole;
  private StudyRoom room;
  private MockHttpSession session;

  // a tutor, a future room, and students in every eligibility state (everything is rolled back after each test)
  @BeforeEach
  void setUp() throws Exception {
    teacher = teacherRepository.save(new Teacher("it.join.teacher@example.com", "it.join.teacher@studymatch.com",
        "Test Teacher", passwordEncoder.encode(PASSWORD)));
    category = categoryRepository.save(new Category("IT Join Category"));
    maths = subjectRepository.save(new Subject("IT-JOIN-MATH", "Test Maths", 6, category));

    AcademicYear year = academicYearRepository.save(new AcademicYear(2091));
    mathsEdition = new SubjectEdition(maths, year, Set.of(teacher));
    mathsEdition.addClassSection("T1");
    mathsEdition = subjectEditionRepository.save(mathsEdition);

    tutor = saveStudent("it.join.tutor@example.com", "Tutor Test");
    enrolledStudent = saveStudent("it.join.enrolled@example.com", "Enrolled Student");
    notEnrolledStudent = saveStudent("it.join.notenrolled@example.com", "Not Enrolled Student");
    passedStudent = saveStudent("it.join.passed@example.com", "Passed Student");

    // the enrolled student is studying maths this year, the passed one finished it
    enrolmentRepository.save(new Enrolment(enrolledStudent, mathsEdition.getClassSection("T1")));
    Enrolment passedEnrolment = new Enrolment(passedStudent, mathsEdition.getClassSection("T1"));
    passedEnrolment.setFinalGrade(15);
    enrolmentRepository.save(passedEnrolment);

    mathsRole = saveTutorRole(tutor, maths);

    room = new StudyRoom(mathsRole, tomorrowAt(14), tomorrowAt(16), 5, "test room", LINK);
    room = studyRoomRepository.save(room);

    session = loginAndGetSession(enrolledStudent.getInstitutionalEmail());
  }

  private Student saveStudent(String personalEmail, String name) {
    Degree degree = degreeRepository.save(new Degree("IT Join Degree " + name, 3));
    Student student = new Student(personalEmail, name, passwordEncoder.encode(PASSWORD), degree, 1);
    student.setMustChangePassword(false);
    student.setPersonalityProfile(new PersonalityProfile(0.5, 0.5, 0.5, 0.5, 0.5));
    student = studentRepository.save(student);
    student.generateInstitutionalEmail("studymatch.com");
    return studentRepository.saveAndFlush(student);
  }

  private TutorRole saveTutorRole(Student student, Subject subject) {
    TutorApplication application = tutorApplicationRepository
        .save(new TutorApplication(student, subject, "test motivation"));
    return tutorRoleRepository.save(application.approve(teacher));
  }

  private MockHttpSession loginAndGetSession(String email) throws Exception {
    MvcResult result = mockMvc.perform(post("/users/login").with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
        .andReturn();
    assertEquals(200, result.getResponse().getStatus());
    return (MockHttpSession) result.getRequest().getSession(false);
  }

  private ResultActions join(Long roomId, MockHttpSession session) throws Exception {
    return mockMvc.perform(post("/study-rooms/" + roomId + "/join").with(csrf()).session(session));
  }

  private static LocalDateTime tomorrowAt(int hour) {
    return LocalDateTime.now().plusDays(1).truncatedTo(ChronoUnit.DAYS).withHour(hour);
  }

  @Test
  @DisplayName("An eligible student joins and becomes a participant")
  void studentJoins() throws Exception {
    // Act + Assert
    join(room.getId(), session)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.participantCount").value(1))
        .andExpect(jsonPath("$.status").value("SCHEDULED"));

    assertEquals(1, studyRoomRepository.findById(room.getId()).orElseThrow().getParticipations().size());
  }

  @Test
  @DisplayName("A student not enrolled in the subject's current edition can't join")
  void notEnrolledCannotJoin() throws Exception {
    // Arrange
    MockHttpSession notEnrolled = loginAndGetSession(notEnrolledStudent.getInstitutionalEmail());

    // Act + Assert
    join(room.getId(), notEnrolled)
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("NOT_ELIGIBLE_FOR_STUDYROOM"));
  }

  @Test
  @DisplayName("A student who already passed the subject can't join")
  void passedCannotJoin() throws Exception {
    // Arrange
    MockHttpSession passed = loginAndGetSession(passedStudent.getInstitutionalEmail());

    // Act + Assert
    join(room.getId(), passed)
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("NOT_ELIGIBLE_FOR_STUDYROOM"));
  }

  @Test
  @DisplayName("A student can't join a room that overlaps one they already joined")
  void overlapCannotJoin() throws Exception {
    // Arrange - another room at 15-17 that the student already joined
    StudyRoom otherRoom = new StudyRoom(mathsRole, tomorrowAt(15), tomorrowAt(17), 5, "other room", LINK);
    otherRoom.join(enrolledStudent);
    otherRoom = studyRoomRepository.save(otherRoom);

    // Act + Assert - the target room is 14-16, overlapping
    join(room.getId(), session)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("JOINED_STUDYROOM_OVERLAP"))
        .andExpect(jsonPath("$.params.id").value(otherRoom.getId()));
  }

  @Test
  @DisplayName("A full room can't be joined")
  void fullRoomCannotJoin() throws Exception {
    // Arrange - a full room with two participants
    StudyRoom fullRoom = new StudyRoom(mathsRole, tomorrowAt(14), tomorrowAt(16), 2, "full room", LINK);
    fullRoom.join(notEnrolledStudent);
    fullRoom.join(passedStudent);
    fullRoom = studyRoomRepository.save(fullRoom);

    // Act + Assert
    join(fullRoom.getId(), session)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("STUDYROOM_FULL"));
  }

  @Test
  @DisplayName("A student can't join the same room twice")
  void cannotJoinTwice() throws Exception {
    // Arrange - join once
    join(room.getId(), session).andExpect(status().isCreated());

    // Act + Assert - join again
    join(room.getId(), session)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("ALREADY_JOINED_STUDYROOM"));
  }

  @Test
  @DisplayName("A student can't join after the room started")
  void cannotJoinAfterStart() throws Exception {
    // Arrange - the room started a minute ago
    ReflectionTestUtils.setField(room, "startsAt", LocalDateTime.now().minusMinutes(1));
    studyRoomRepository.saveAndFlush(room);

    // Act + Assert
    join(room.getId(), session)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("STUDYROOM_ALREADY_STARTED"));
  }

  @Test
  @DisplayName("A room that doesn't exist is 404")
  void unknownRoom() throws Exception {
    join(9_999_999L, session).andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("Teachers and unauthenticated users cannot join")
  void onlyStudentsCanJoin() throws Exception {
    teacher.setMustChangePassword(false);
    teacherRepository.saveAndFlush(teacher);

    mockMvc.perform(post("/study-rooms/" + room.getId() + "/join").with(csrf())
        .session(loginAndGetSession(teacher.getInstitutionalEmail())))
        .andExpect(status().isForbidden());
    mockMvc.perform(post("/study-rooms/" + room.getId() + "/join").with(csrf()))
        .andExpect(status().isUnauthorized());
  }

}
