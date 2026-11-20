package com.studymatch.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

import com.studymatch.models.Category;
import com.studymatch.models.Degree;
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
import com.studymatch.services.RatingService;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RateTutorIntegrationTest {

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

  @Autowired
  private RatingService ratingService;

  private Student tutor;
  private Student ana;
  private Student bruno;
  private TutorRole role;
  private StudyRoom room;
  private MockHttpSession session;

  // ana and bruno joined a room that ended an hour ago, only ana came. ana is logged in (rolled back after each test)
  @BeforeEach
  void setUp() throws Exception {
    Teacher teacher = teacherRepository.save(new Teacher("it.rt.teacher@example.com", "it.rt.teacher@studymatch.com",
        "Test Teacher", passwordEncoder.encode(PASSWORD)));
    Category category = categoryRepository.save(new Category("IT Rating Category"));
    Subject maths = subjectRepository.save(new Subject("IT-RT-MATH", "Test Maths", 6, category));
    tutor = saveStudent("it.rt.tutor@example.com", "Tutor Test");
    role = tutorRoleRepository.save(tutorApplicationRepository
        .save(new TutorApplication(tutor, maths, "test motivation")).approve(teacher));
    ana = saveStudent("it.rt.ana@example.com", "Ana Test");
    bruno = saveStudent("it.rt.bruno@example.com", "Bruno Test");

    room = endedRoom(LocalDateTime.now().minusHours(1), Set.of(ana.getNumber()), ana, bruno);
    session = loginAndGetSession(ana.getInstitutionalEmail());
  }

  private Student saveStudent(String personalEmail, String name) {
    Degree degree = degreeRepository.save(new Degree("IT Rating Degree " + name, 3));
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

  // the constructor won't take past dates, so the room is moved back after the students joined.
  // the tutor confirms right after it ended (48 hours later it's too late), then it goes back to endsAt
  private StudyRoom endedRoom(LocalDateTime endsAt, Set<Long> attendees, Student... joined) {
    LocalDateTime tomorrow = LocalDateTime.now().plusDays(1).truncatedTo(ChronoUnit.DAYS).withHour(14);
    StudyRoom ended = new StudyRoom(role, tomorrow, tomorrow.plusHours(2), 5, "test room", LINK);
    for (Student student : joined) {
      ended.join(student);
    }
    moveRoom(ended, LocalDateTime.now().minusMinutes(1));
    ended.confirmAttendance(attendees);
    moveRoom(ended, endsAt);
    return studyRoomRepository.saveAndFlush(ended);
  }

  private static void moveRoom(StudyRoom moved, LocalDateTime endsAt) {
    ReflectionTestUtils.setField(moved, "startsAt", endsAt.minusHours(2));
    ReflectionTestUtils.setField(moved, "endsAt", endsAt);
  }

  private ResultActions rate(StudyRoom rated, String body) throws Exception {
    return mockMvc.perform(post("/study-rooms/" + rated.getId() + "/rating").with(csrf()).session(session)
        .contentType(MediaType.APPLICATION_JSON)
        .content(body));
  }

  private void saveRating(StudyRoom rated, Student student, int stars) {
    rated.getParticipations().stream()
        .filter(p -> p.getStudent().getNumber().equals(student.getNumber()))
        .findFirst()
        .ifPresent(p -> ratingRepository.saveAndFlush(new Rating(p, stars, null)));
  }

  @Test
  @DisplayName("Ana rates the tutor, and anyone sees the comment on the tutor's ratings without her name")
  void attendeeRatesTheTutor() throws Exception {
    // Act
    rate(room, "{\"stars\": 5, \"comment\": \"explained loops really well\"}")
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.stars").value(5))
        .andExpect(jsonPath("$.subject").value("Test Maths"));

    // Assert - bruno reads them
    session = loginAndGetSession(bruno.getInstitutionalEmail());
    mockMvc.perform(get("/students/" + tutor.getNumber() + "/ratings").session(session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].comment").value("explained loops really well"))
        .andExpect(jsonPath("$[0].ratedAt").isNotEmpty())
        .andExpect(jsonPath("$[0].studentNumber").doesNotExist())
        .andExpect(jsonPath("$[0].name").doesNotExist());
  }

  @Test
  @DisplayName("Rating the same room twice gets 409 ROOM_ALREADY_RATED and only the first is kept")
  void rateTwice() throws Exception {
    // Arrange
    rate(room, "{\"stars\": 5}").andExpect(status().isCreated());

    // Act + Assert
    rate(room, "{\"stars\": 1}")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("ROOM_ALREADY_RATED"));
    assertEquals(1, ratingService.countRatings(role));
  }

  @Test
  @DisplayName("Bruno joined but didn't come, so he gets ATTENDANCE_NOT_CONFIRMED")
  void absentCannotRate() throws Exception {
    // Arrange
    session = loginAndGetSession(bruno.getInstitutionalEmail());

    // Act + Assert
    rate(room, "{\"stars\": 1}")
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("ATTENDANCE_NOT_CONFIRMED"));
  }

  @Test
  @DisplayName("Someone who never joined the room gets NOT_A_PARTICIPANT")
  void outsiderCannotRate() throws Exception {
    // Arrange
    Student carla = saveStudent("it.rt.carla@example.com", "Carla Test");
    session = loginAndGetSession(carla.getInstitutionalEmail());

    // Act + Assert
    rate(room, "{\"stars\": 3}")
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("NOT_A_PARTICIPANT"));
  }

  @Test
  @DisplayName("More than 7 days after the room ended it's too late, RATING_WINDOW_CLOSED")
  void afterSevenDays() throws Exception {
    // Arrange
    StudyRoom old = endedRoom(LocalDateTime.now().minusDays(Rating.RATING_WINDOW_DAYS).minusHours(1),
        Set.of(ana.getNumber()), ana);

    // Act + Assert
    rate(old, "{\"stars\": 4}")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("RATING_WINDOW_CLOSED"));
  }

  @Test
  @DisplayName("Stars outside 1 to 5 are rejected before anything else")
  void starsOutOfRange() throws Exception {
    // Act + Assert
    rate(room, "{\"stars\": 6}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.fieldErrors[0].field").value("stars"));
  }

  @Test
  @DisplayName("The tutor's average counts each student once: ana's 5 and 5 with bruno's 2 is 3.5, not 4")
  void averageCountsEachStudentOnce() {
    // Arrange - a second room both came to
    StudyRoom second = endedRoom(LocalDateTime.now().minusHours(5), Set.of(ana.getNumber(), bruno.getNumber()),
        ana, bruno);
    saveRating(room, ana, 5);
    saveRating(second, ana, 5);
    saveRating(second, bruno, 2);

    // Act
    Double average = ratingService.getAverageStars(role);

    // Assert - the count is still every rating
    assertEquals(3.5, average);
    assertEquals(3, ratingService.countRatings(role));
  }

}
