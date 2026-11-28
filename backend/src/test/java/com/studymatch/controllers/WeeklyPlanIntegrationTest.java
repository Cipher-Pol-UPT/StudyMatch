package com.studymatch.controllers;

import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

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

import com.studymatch.enums.RoomType;
import com.studymatch.models.AcademicYear;
import com.studymatch.models.Category;
import com.studymatch.models.Degree;
import com.studymatch.models.Staff;
import com.studymatch.models.Subject;
import com.studymatch.models.Teacher;
import com.studymatch.models.WeeklySession;
import com.studymatch.repositories.AcademicYearRepository;
import com.studymatch.repositories.CategoryRepository;
import com.studymatch.repositories.DegreeRepository;
import com.studymatch.repositories.StaffRepository;
import com.studymatch.repositories.SubjectRepository;
import com.studymatch.repositories.TeacherRepository;

import jakarta.persistence.EntityManager;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class WeeklyPlanIntegrationTest {

  private static final String PASSWORD = "Test1234";
  private static final String NEW_CODE = "ITPLAN2";
  // far in the future so it can't clash with real academic years
  private static final int YEAR = 2096;

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private EntityManager entityManager;

  @Autowired
  private StaffRepository staffRepository;

  @Autowired
  private TeacherRepository teacherRepository;

  @Autowired
  private CategoryRepository categoryRepository;

  @Autowired
  private DegreeRepository degreeRepository;

  @Autowired
  private AcademicYearRepository academicYearRepository;

  @Autowired
  private SubjectRepository subjectRepository;

  @Autowired
  private PasswordEncoder passwordEncoder;

  private Category category;
  private Degree degree;
  private Teacher teacher;
  private Subject subject;
  private MockHttpSession staff;

  // a subject with 2 sessions of 120 minutes, a classroom one then a lab one
  @BeforeEach
  void setUp() throws Exception {
    category = categoryRepository.save(new Category("IT Plan Category"));
    degree = degreeRepository.save(new Degree("IT Plan Degree", 3));
    academicYearRepository.save(new AcademicYear(YEAR));
    teacher = teacherRepository.save(new Teacher("it.plan.teacher@example.com", "it.plan.teacher@studymatch.com",
        "IT Plan Teacher", passwordEncoder.encode(PASSWORD)));

    Subject toSave = new Subject("ITPLAN1", "IT Plan Subject", 6, category);
    toSave.addDegree(degree, 1, 1);
    toSave.setWeeklySessions(List.of(new WeeklySession(120, RoomType.CLASSROOM), new WeeklySession(120, RoomType.LAB)));
    subject = subjectRepository.save(toSave);

    Staff member = new Staff("it.plan.staff@example.com", "it.plan.staff@studymatch.com", "Test Staff",
        passwordEncoder.encode(PASSWORD));
    member.setMustChangePassword(false);
    staffRepository.save(member);
    staff = loginAs("it.plan.staff@studymatch.com");
  }

  private MockHttpSession loginAs(String email) throws Exception {
    MvcResult result = mockMvc.perform(post("/users/login").with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
        .andReturn();
    assertEquals(200, result.getResponse().getStatus());
    return (MockHttpSession) result.getRequest().getSession(false);
  }

  private static String session(Object minutes, String roomType) {
    return "{\"minutes\":" + minutes + ",\"roomType\":\"" + roomType + "\"}";
  }

  private static String sessions(String... sessions) {
    return "[" + String.join(",", sessions) + "]";
  }

  // a new subject in one degree with this year's edition, and the plan only when there is one
  private ResultActions register(String weeklySessions) throws Exception {
    String body = "{\"code\":\"" + NEW_CODE + "\",\"name\":\"IT Plan New\",\"ects\":6,\"categoryId\":"
        + category.getId() + ",\"degrees\":[{\"degreeId\":" + degree.getId() + ",\"curricularYear\":1,\"semester\":1}]"
        + ",\"edition\":{\"academicYear\":" + YEAR + ",\"teacherNumbers\":[" + teacher.getNumber() + "]"
        + ",\"classSections\":[\"T1\"]}"
        + (weeklySessions == null ? "" : ",\"weeklySessions\":" + weeklySessions) + "}";
    return mockMvc.perform(post("/subjects/").with(csrf()).session(staff)
        .contentType(MediaType.APPLICATION_JSON)
        .content(body));
  }

  private ResultActions update(String weeklySessions) throws Exception {
    String body = "{\"name\":\"IT Plan Subject\",\"ects\":6"
        + (weeklySessions == null ? "" : ",\"weeklySessions\":" + weeklySessions) + "}";
    return mockMvc.perform(put("/subjects/" + subject.getId()).with(csrf()).session(staff)
        .contentType(MediaType.APPLICATION_JSON)
        .content(body));
  }

  // read what the request saved, not the copies this test already holds
  private List<String> savedPlan(String code) {
    entityManager.flush();
    entityManager.clear();
    return subjectRepository.findByCodeIgnoreCase(code).orElseThrow().getWeeklySessions().stream()
        .map(s -> s.getMinutes() + " " + s.getRoomType())
        .toList();
  }

  // register subject

  @Test
  @DisplayName("Registering with a plan is 201 with the sessions in the order given, and they're saved")
  void registerWithPlan() throws Exception {
    // Act
    register(sessions(session(90, "AUDITORIUM"), session(180, "LAB")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.subject.weeklySessions.length()").value(2))
        .andExpect(jsonPath("$.subject.weeklySessions[0].minutes").value(90))
        .andExpect(jsonPath("$.subject.weeklySessions[0].roomType").value("AUDITORIUM"))
        .andExpect(jsonPath("$.subject.weeklySessions[1].minutes").value(180))
        .andExpect(jsonPath("$.subject.weeklySessions[1].roomType").value("LAB"));

    // Assert
    assertEquals(List.of("90 AUDITORIUM", "180 LAB"), savedPlan(NEW_CODE));
  }

  @Test
  @DisplayName("Registering without a plan is 201 with no sessions")
  void registerWithoutPlan() throws Exception {
    // Act
    register(null)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.subject.weeklySessions.length()").value(0));

    // Assert
    assertTrue(savedPlan(NEW_CODE).isEmpty());
  }

  @Test
  @DisplayName("A length that isn't allowed is 400 INVALID_SESSION_LENGTH and the subject isn't saved")
  void registerInvalidLength() throws Exception {
    // Act
    register(sessions(session(120, "CLASSROOM"), session(45, "LAB")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_SESSION_LENGTH"));

    // Assert
    assertTrue(subjectRepository.findByCodeIgnoreCase(NEW_CODE).isEmpty());
  }

  @Test
  @DisplayName("6 sessions is 400 TOO_MANY_WEEKLY_SESSIONS with max 5, and the subject isn't saved")
  void registerTooManySessions() throws Exception {
    // Arrange
    String one = session(60, "CLASSROOM");

    // Act
    register(sessions(one, one, one, one, one, one))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("TOO_MANY_WEEKLY_SESSIONS"))
        .andExpect(jsonPath("$.params.max").value(5));

    // Assert
    assertTrue(subjectRepository.findByCodeIgnoreCase(NEW_CODE).isEmpty());
  }

  @Test
  @DisplayName("5 sessions of 180 minutes, the most there can be, are accepted")
  void registerFullestPlan() throws Exception {
    // Arrange
    String longest = session(180, "LAB");

    // Act
    register(sessions(longest, longest, longest, longest, longest))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.subject.weeklySessions.length()").value(5));
  }

  @Test
  @DisplayName("A room type that doesn't exist is 400 VALIDATION_FAILED on roomType")
  void registerUnknownRoomType() throws Exception {
    register(sessions(session(120, "POOL")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.fieldErrors[0].field").value("roomType"));
  }

  @Test
  @DisplayName("A session without its minutes is 400 VALIDATION_FAILED on that session's minutes")
  void registerMissingMinutes() throws Exception {
    register(sessions("{\"roomType\":\"LAB\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.fieldErrors[0].field").value("weeklySessions[0].minutes"));
  }

  // update subject

  @Test
  @DisplayName("Updating with a plan replaces the old one: 200 with only the new sessions")
  void updateReplacesPlan() throws Exception {
    // Act
    update(sessions(session(150, "AUDITORIUM")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.weeklySessions.length()").value(1))
        .andExpect(jsonPath("$.weeklySessions[0].minutes").value(150))
        .andExpect(jsonPath("$.weeklySessions[0].roomType").value("AUDITORIUM"));

    // Assert
    assertEquals(List.of("150 AUDITORIUM"), savedPlan("ITPLAN1"));
  }

  @Test
  @DisplayName("Updating without a plan leaves it as it was")
  void updateWithoutPlanKeepsIt() throws Exception {
    // Act
    update(null)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.weeklySessions.length()").value(2));

    // Assert
    assertEquals(List.of("120 CLASSROOM", "120 LAB"), savedPlan("ITPLAN1"));
  }

  @Test
  @DisplayName("Updating with an empty plan clears it")
  void updateEmptyPlanClears() throws Exception {
    // Act
    update("[]")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.weeklySessions.length()").value(0));

    // Assert
    assertTrue(savedPlan("ITPLAN1").isEmpty());
  }

  @Test
  @DisplayName("A refused plan in the update is 400 and the old plan stays")
  void updateRefusedKeepsPlan() throws Exception {
    // Act
    update(sessions(session(200, "LAB")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_SESSION_LENGTH"));

    // Assert
    assertEquals(List.of("120 CLASSROOM", "120 LAB"), savedPlan("ITPLAN1"));
  }

  // reading it back

  @Test
  @DisplayName("The subject and the subject list both show the plan in order")
  void planIsShown() throws Exception {
    mockMvc.perform(get("/subjects/" + subject.getId()).session(staff))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.weeklySessions[0].roomType").value("CLASSROOM"))
        .andExpect(jsonPath("$.weeklySessions[1].roomType").value("LAB"));
    mockMvc.perform(get("/subjects/").session(staff))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.code == 'ITPLAN1')].weeklySessions[1].roomType", hasItem("LAB")));
  }

}
