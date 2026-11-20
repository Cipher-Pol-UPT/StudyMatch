package com.studymatch.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
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

import com.studymatch.models.Category;
import com.studymatch.models.Degree;
import com.studymatch.models.Participation;
import com.studymatch.models.PersonalityProfile;
import com.studymatch.models.Rating;
import com.studymatch.models.Student;
import com.studymatch.models.StudyRoom;
import com.studymatch.models.Subject;
import com.studymatch.models.Teacher;
import com.studymatch.models.TutorApplication;
import com.studymatch.models.TutorRole;
import com.studymatch.repositories.CategoryRepository;
import com.studymatch.repositories.DegreeRepository;
import com.studymatch.repositories.RatingRepository;
import com.studymatch.repositories.StudentRepository;
import com.studymatch.repositories.StudyRoomRepository;
import com.studymatch.repositories.SubjectRepository;
import com.studymatch.repositories.TeacherRepository;
import com.studymatch.repositories.TutorApplicationRepository;
import com.studymatch.repositories.TutorRoleRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ViewStudyRoomsIntegrationTest {

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
  private TutorApplicationRepository tutorApplicationRepository;
  @Autowired
  private TutorRoleRepository tutorRoleRepository;
  @Autowired
  private StudyRoomRepository studyRoomRepository;
  @Autowired
  private RatingRepository ratingRepository;

  private Teacher teacher;
  private Category category;
  private Student tutor;
  private Student otherTutor;
  private Student viewer;
  private Student ana;
  private Student bruno;
  private Subject maths;
  private Subject physics;
  private TutorRole mathsRole;
  private TutorRole physicsRole;
  private TutorRole otherMathsRole;
  private StudyRoom openRoom;
  private StudyRoom physicsRoom;
  private StudyRoom fullRoom;

  // two tutors (one of two subjects), a viewer, and rooms in every state (everything is rolled back after each test)
  @BeforeEach
  void setUp() {
    teacher = new Teacher("it.vr.teacher@example.com", "it.vr.teacher@studymatch.com", "Test Teacher",
        passwordEncoder.encode(PASSWORD));
    teacher.setMustChangePassword(false);
    teacher = teacherRepository.save(teacher);

    category = categoryRepository.save(new Category("IT View Rooms Category"));
    maths = subjectRepository.save(new Subject("IT-VR-MATH", "Test Maths", 6, category));
    physics = subjectRepository.save(new Subject("IT-VR-PHYS", "Test Physics", 6, category));

    tutor = saveStudent("it.vr.tutor@example.com", "Tutor Test");
    otherTutor = saveStudent("it.vr.other@example.com", "Other Tutor");
    viewer = saveStudent("it.vr.viewer@example.com", "Viewer Student");
    ana = saveStudent("it.vr.ana@example.com", "Ana View");
    bruno = saveStudent("it.vr.bruno@example.com", "Bruno View");

    mathsRole = saveTutorRole(tutor, maths);
    physicsRole = saveTutorRole(tutor, physics);
    otherMathsRole = saveTutorRole(otherTutor, maths);

    openRoom = saveRoom(mathsRole, tomorrowAt(14), tomorrowAt(16), 5, "Integration practice");
    physicsRoom = saveRoom(physicsRole, tomorrowAt(16), tomorrowAt(18), 6, "Physics revision");

    fullRoom = new StudyRoom(otherMathsRole, daysFromNowAt(2, 14), daysFromNowAt(2, 16), 2, "full room", LINK);
    fullRoom.join(ana);
    fullRoom.join(bruno);
    fullRoom = studyRoomRepository.save(fullRoom);

    saveCancelledRoom();
    saveCompletedRoomWithRating();
    saveStaleRoom();
  }

  private Student saveStudent(String personalEmail, String name) {
    Degree degree = degreeRepository.save(new Degree("IT View Rooms Degree " + name, 3));
    Student student = new Student(personalEmail, name, passwordEncoder.encode(PASSWORD), degree, 1);
    student.setMustChangePassword(false);
    student.setPersonalityProfile(new PersonalityProfile(0.5, 0.5, 0.5, 0.5));
    student = studentRepository.save(student);
    student.generateInstitutionalEmail("studymatch.com");
    return studentRepository.saveAndFlush(student);
  }

  private TutorRole saveTutorRole(Student student, Subject subject) {
    TutorApplication application = tutorApplicationRepository
        .save(new TutorApplication(student, subject, "test motivation"));
    return tutorRoleRepository.save(application.approve(teacher));
  }

  private StudyRoom saveRoom(TutorRole role, LocalDateTime startsAt, LocalDateTime endsAt, int capacity,
      String description) {
    return studyRoomRepository.save(new StudyRoom(role, startsAt, endsAt, capacity, description, LINK));
  }

  // a room the tutor confirmed an hour ago, ana attended and rated 5 stars
  private void saveCompletedRoomWithRating() {
    StudyRoom room = new StudyRoom(mathsRole, tomorrowAt(14), tomorrowAt(16), 5, "completed room", LINK);
    room.join(ana);
    ReflectionTestUtils.setField(room, "startsAt", LocalDateTime.now().minusHours(3));
    ReflectionTestUtils.setField(room, "endsAt", LocalDateTime.now().minusHours(1));
    room.confirmAttendance(Set.of(ana.getNumber()));
    room = studyRoomRepository.save(room);
    Participation participation = room.getParticipations().iterator().next();
    ratingRepository.save(new Rating(participation, 5, "great"));
  }

  private void saveCancelledRoom() {
    StudyRoom room = new StudyRoom(mathsRole, daysFromNowAt(3, 14), daysFromNowAt(3, 16), 5, "cancelled room", LINK);
    room.cancel("test reason");
    studyRoomRepository.save(room);
  }

  // stored SCHEDULED but 49 hours past its end, so it counts as completed
  private void saveStaleRoom() {
    StudyRoom room = new StudyRoom(mathsRole, tomorrowAt(14), tomorrowAt(16), 5, "stale room", LINK);
    ReflectionTestUtils.setField(room, "startsAt", LocalDateTime.now().minusHours(51));
    ReflectionTestUtils.setField(room, "endsAt", LocalDateTime.now().minusHours(49));
    studyRoomRepository.save(room);
  }

  private MockHttpSession login(String email) throws Exception {
    MvcResult result = mockMvc.perform(post("/users/login").with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
        .andReturn();
    assertEquals(200, result.getResponse().getStatus());
    return (MockHttpSession) result.getRequest().getSession(false);
  }

  private ResultActions listAs(String email, String query) throws Exception {
    return mockMvc.perform(get("/study-rooms/" + query).session(login(email)));
  }

  private ResultActions detailAs(String email, Long roomId) throws Exception {
    return mockMvc.perform(get("/study-rooms/" + roomId).session(login(email)));
  }

  private static LocalDateTime tomorrowAt(int hour) {
    return daysFromNowAt(1, hour);
  }

  private static LocalDateTime daysFromNowAt(int days, int hour) {
    return LocalDateTime.now().plusDays(days).truncatedTo(ChronoUnit.DAYS).withHour(hour);
  }

  // the calendar

  @Test
  @DisplayName("The calendar shows only the open rooms, sorted by start: cancelled, completed and stale are hidden")
  void listsOnlyOpenRooms() throws Exception {
    // Act + Assert
    listAs("it.vr.viewer@example.com", "")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(3))
        .andExpect(jsonPath("$[0].id").value(openRoom.getId()))
        .andExpect(jsonPath("$[0].availablePlaces").value(5))
        .andExpect(jsonPath("$[1].id").value(physicsRoom.getId()))
        .andExpect(jsonPath("$[2].id").value(fullRoom.getId()));
  }

  @Test
  @DisplayName("Filters can be combined: subject, tutor, topic and date each narrow the calendar")
  void filters() throws Exception {
    listAs("it.vr.viewer@example.com", "?subjectId=" + physics.getId())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].id").value(physicsRoom.getId()));

    listAs("it.vr.viewer@example.com", "?tutor=other")
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].id").value(fullRoom.getId()));

    listAs("it.vr.viewer@example.com", "?topic=revision")
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].id").value(physicsRoom.getId()));

    listAs("it.vr.viewer@example.com", "?date=" + LocalDate.now().plusDays(1))
        .andExpect(jsonPath("$.length()").value(2));
  }

  @Test
  @DisplayName("A filter that matches nothing is an empty list, not an error")
  void noMatches() throws Exception {
    listAs("it.vr.viewer@example.com", "?topic=missing")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
  }

  @Test
  @DisplayName("Teachers and unauthenticated users cannot open the calendar")
  void listOnlyForStudents() throws Exception {
    mockMvc.perform(get("/study-rooms/").session(login("it.vr.teacher@studymatch.com")))
        .andExpect(status().isForbidden());
    mockMvc.perform(get("/study-rooms/")).andExpect(status().isUnauthorized());
  }

  // picking one

  @Test
  @DisplayName("An open room shows its description, the tutor's rating in that subject, and can be joined")
  void detailOfOpenRoom() throws Exception {
    detailAs("it.vr.viewer@example.com", openRoom.getId())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.description").value("Integration practice"))
        .andExpect(jsonPath("$.availablePlaces").value(5))
        .andExpect(jsonPath("$.tutorAverageRating").value(5.0))
        .andExpect(jsonPath("$.tutorRatingCount").value(1))
        .andExpect(jsonPath("$.hosted").value(false))
        .andExpect(jsonPath("$.joined").value(false))
        .andExpect(jsonPath("$.canJoin").value(true));
  }

  @Test
  @DisplayName("The host opens their own room: hosted and no join option")
  void detailForHost() throws Exception {
    detailAs("it.vr.tutor@example.com", openRoom.getId())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.hosted").value(true))
        .andExpect(jsonPath("$.canJoin").value(false));
  }

  @Test
  @DisplayName("A student who already joined can't join again")
  void detailForJoinedStudent() throws Exception {
    detailAs("it.vr.ana@example.com", fullRoom.getId())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.hosted").value(false))
        .andExpect(jsonPath("$.joined").value(true))
        .andExpect(jsonPath("$.canJoin").value(false));
  }

  @Test
  @DisplayName("A room that doesn't exist is 404")
  void unknownRoom() throws Exception {
    detailAs("it.vr.viewer@example.com", 99999999L).andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("Teachers cannot open a room's detail")
  void detailOnlyForStudents() throws Exception {
    mockMvc.perform(get("/study-rooms/" + openRoom.getId()).session(login("it.vr.teacher@studymatch.com")))
        .andExpect(status().isForbidden());
  }

}
