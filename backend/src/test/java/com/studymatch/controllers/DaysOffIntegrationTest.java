package com.studymatch.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

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

import com.studymatch.models.AcademicYear;
import com.studymatch.models.DayOff;
import com.studymatch.models.Staff;
import com.studymatch.models.Teacher;
import com.studymatch.repositories.AcademicYearRepository;
import com.studymatch.repositories.DayOffRepository;
import com.studymatch.repositories.StaffRepository;
import com.studymatch.repositories.TeacherRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class DaysOffIntegrationTest {

  private static final String PASSWORD = "Test1234";
  // far in the future so it's newer than any real year in the test database
  private static final int YEAR = 2097;
  private static final String PATH = "/academic-years/" + YEAR + "/days-off";

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private PasswordEncoder passwordEncoder;

  @Autowired
  private StaffRepository staffRepository;

  @Autowired
  private TeacherRepository teacherRepository;

  @Autowired
  private AcademicYearRepository academicYearRepository;

  @Autowired
  private DayOffRepository dayOffRepository;

  private AcademicYear current;
  private MockHttpSession staff;

  // semester 1 from 15 September to 31 January, semester 2 from 15 February to 30 June
  @BeforeEach
  void setUp() throws Exception {
    academicYearRepository.save(new AcademicYear(YEAR - 1));
    current = new AcademicYear(YEAR);
    current.setSemesters(LocalDate.of(YEAR, 9, 15), LocalDate.of(YEAR + 1, 1, 31), LocalDate.of(YEAR + 1, 2, 15),
        LocalDate.of(YEAR + 1, 6, 30));
    current = academicYearRepository.save(current);

    Staff member = new Staff("it.daysoff.staff@example.com", "it.daysoff.staff@studymatch.com", "Test Staff",
        passwordEncoder.encode(PASSWORD));
    member.setMustChangePassword(false);
    staffRepository.save(member);
    staff = loginAs("it.daysoff.staff@studymatch.com");
  }

  private MockHttpSession loginAs(String email) throws Exception {
    MvcResult result = mockMvc.perform(post("/users/login").with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
        .andReturn();
    assertEquals(200, result.getResponse().getStatus());
    return (MockHttpSession) result.getRequest().getSession(false);
  }

  private ResultActions add(MockHttpSession session, String path, String body) throws Exception {
    return mockMvc.perform(post(path).with(csrf()).session(session).contentType(MediaType.APPLICATION_JSON).content(body));
  }

  private static String day(String firstDay, String lastDay, String reason) {
    return "{\"firstDay\":\"" + firstDay + "\"," + (lastDay == null ? "" : "\"lastDay\":\"" + lastDay + "\",")
        + "\"reason\":\"" + reason + "\"}";
  }

  private DayOff saved(int month, int day, int lastMonth, int lastDay, String reason) {
    int year = month >= 9 ? YEAR : YEAR + 1;
    int lastYear = lastMonth >= 9 ? YEAR : YEAR + 1;
    return dayOffRepository.save(new DayOff(current, LocalDate.of(year, month, day), LocalDate.of(lastYear, lastMonth, lastDay), reason));
  }

  @Test
  @DisplayName("Staff marks one day: 201 with the same first and last day, saved, and listed")
  void staffAddsDay() throws Exception {
    // Act
    add(staff, PATH, day(YEAR + "-12-08", null, "  Immaculate Conception "))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.firstDay").value(YEAR + "-12-08"))
        .andExpect(jsonPath("$.lastDay").value(YEAR + "-12-08"))
        .andExpect(jsonPath("$.reason").value("Immaculate Conception"));

    // Assert
    assertEquals(1, dayOffRepository.findByAcademicYearOrderByFirstDay(current).size());
    mockMvc.perform(get(PATH).session(staff))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].firstDay").value(YEAR + "-12-08"))
        .andExpect(jsonPath("$[0].reason").value("Immaculate Conception"));
  }

  @Test
  @DisplayName("Staff marks a range, and the list has the earliest first")
  void staffAddsRange() throws Exception {
    // Arrange
    saved(12, 8, 12, 8, "Immaculate Conception");

    // Act
    add(staff, PATH, day(YEAR + "-10-26", YEAR + "-10-30", "Mid-term break"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.lastDay").value(YEAR + "-10-30"));

    // Assert
    mockMvc.perform(get(PATH).session(staff))
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[0].reason").value("Mid-term break"))
        .andExpect(jsonPath("$[1].reason").value("Immaculate Conception"));
  }

  @Test
  @DisplayName("A range ending before it starts is 400 DAY_OFF_ENDS_BEFORE_START, and nothing is saved")
  void endsBeforeStart() throws Exception {
    // Act + Assert
    add(staff, PATH, day(YEAR + "-10-30", YEAR + "-10-26", "Mid-term break"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("DAY_OFF_ENDS_BEFORE_START"));
    assertTrue(dayOffRepository.findByAcademicYearOrderByFirstDay(current).isEmpty());
  }

  @Test
  @DisplayName("A day between the semesters, or a range across them, is 400 DAY_OFF_OUTSIDE_SEMESTERS")
  void outsideSemesters() throws Exception {
    // Act + Assert
    add(staff, PATH, day((YEAR + 1) + "-02-05", null, "Break"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("DAY_OFF_OUTSIDE_SEMESTERS"));
    add(staff, PATH, day((YEAR + 1) + "-01-25", (YEAR + 1) + "-02-20", "Exams"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("DAY_OFF_OUTSIDE_SEMESTERS"));
    assertTrue(dayOffRepository.findByAcademicYearOrderByFirstDay(current).isEmpty());
  }

  @Test
  @DisplayName("A day already marked is 409 DAY_OFF_OVERLAPS naming the one it falls on")
  void overlaps() throws Exception {
    // Arrange
    saved(12, 21, 12, 31, "Christmas");

    // Act + Assert
    add(staff, PATH, day(YEAR + "-12-18", YEAR + "-12-21", "Exams"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("DAY_OFF_OVERLAPS"))
        .andExpect(jsonPath("$.params.firstDay").value(YEAR + "-12-21"))
        .andExpect(jsonPath("$.params.lastDay").value(YEAR + "-12-31"))
        .andExpect(jsonPath("$.params.reason").value("Christmas"));
    assertEquals(1, dayOffRepository.findByAcademicYearOrderByFirstDay(current).size());
  }

  @Test
  @DisplayName("A blank reason is a field error on it")
  void blankReason() throws Exception {
    // Act + Assert
    add(staff, PATH, day(YEAR + "-12-08", null, "   "))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.fieldErrors[0].field").value("reason"));
  }

  @Test
  @DisplayName("Staff removes one: 204 and it's gone; one that isn't there is 404")
  void staffRemoves() throws Exception {
    // Arrange
    DayOff christmas = saved(12, 21, 12, 31, "Christmas");

    // Act + Assert
    mockMvc.perform(delete(PATH + "/" + christmas.getId()).with(csrf()).session(staff))
        .andExpect(status().isNoContent());
    assertTrue(dayOffRepository.findById(christmas.getId()).isEmpty());
    mockMvc.perform(delete(PATH + "/" + christmas.getId()).with(csrf()).session(staff))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
  }

  @Test
  @DisplayName("An older year is 409 ACADEMIC_YEAR_NOT_CURRENT, and an unknown one 404")
  void olderOrUnknownYear() throws Exception {
    // Act + Assert
    add(staff, "/academic-years/" + (YEAR - 1) + "/days-off", day((YEAR - 1) + "-12-08", null, "Holiday"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("ACADEMIC_YEAR_NOT_CURRENT"));
    mockMvc.perform(get("/academic-years/" + (YEAR + 50) + "/days-off").session(staff))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("A teacher can read the days but not mark one (403 ACCESS_DENIED)")
  void teacherReadsOnly() throws Exception {
    // Arrange
    saved(12, 21, 12, 31, "Christmas");
    Teacher teacher = new Teacher("it.daysoff.teacher@example.com", "it.daysoff.teacher@studymatch.com", "Test Teacher",
        passwordEncoder.encode(PASSWORD));
    teacher.setMustChangePassword(false);
    teacherRepository.save(teacher);
    MockHttpSession session = loginAs("it.daysoff.teacher@studymatch.com");

    // Act + Assert
    mockMvc.perform(get(PATH).session(session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].reason").value("Christmas"));
    add(session, PATH, day(YEAR + "-12-08", null, "Holiday"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
  }

}
