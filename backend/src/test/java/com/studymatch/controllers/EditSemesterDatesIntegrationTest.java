package com.studymatch.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import com.studymatch.models.Staff;
import com.studymatch.models.Teacher;
import com.studymatch.repositories.AcademicYearRepository;
import com.studymatch.repositories.StaffRepository;
import com.studymatch.repositories.TeacherRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class EditSemesterDatesIntegrationTest {

  private static final String PASSWORD = "Test1234";
  // far in the future so it's newer than any real year in the test database
  private static final int CURRENT_YEAR = 2094;

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

  private MockHttpSession staff;

  // the current year starts with these dates
  @BeforeEach
  void setUp() throws Exception {
    academicYearRepository.save(new AcademicYear(CURRENT_YEAR - 1));
    AcademicYear current = new AcademicYear(CURRENT_YEAR);
    current.setSemesters(LocalDate.of(CURRENT_YEAR, 9, 15), LocalDate.of(CURRENT_YEAR + 1, 1, 31),
        LocalDate.of(CURRENT_YEAR + 1, 2, 15), LocalDate.of(CURRENT_YEAR + 1, 6, 30));
    academicYearRepository.save(current);

    Staff member = new Staff("it.semesters.staff@example.com", "it.semesters.staff@studymatch.com", "Test Staff",
        passwordEncoder.encode(PASSWORD));
    member.setMustChangePassword(false);
    staffRepository.save(member);
    staff = loginAs("it.semesters.staff@studymatch.com");
  }

  private MockHttpSession loginAs(String email) throws Exception {
    MvcResult result = mockMvc.perform(post("/users/login").with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
        .andReturn();
    assertEquals(200, result.getResponse().getStatus());
    return (MockHttpSession) result.getRequest().getSession(false);
  }

  // semester 1 from s1Start, the rest are fine dates for that year
  private static String dates(int year, String s1Start) {
    return "{\"semester1Start\":\"" + s1Start + "\",\"semester1End\":\"" + (year + 1) + "-02-07\",\"semester2Start\":\""
        + (year + 1) + "-02-22\",\"semester2End\":\"" + (year + 1) + "-07-07\"}";
  }

  private ResultActions edit(MockHttpSession session, int year, String body) throws Exception {
    return mockMvc.perform(put("/academic-years/" + year + "/semesters").with(csrf()).session(session)
        .contentType(MediaType.APPLICATION_JSON)
        .content(body));
  }

  private LocalDate semester1StartInDatabase() {
    return academicYearRepository.findById(CURRENT_YEAR).orElseThrow().getSemester1Start();
  }

  @Test
  @DisplayName("Staff changes the current year's dates: 200 with them, saved, and listed with the year")
  void staffEditsDates() throws Exception {
    // Act
    edit(staff, CURRENT_YEAR, dates(CURRENT_YEAR, CURRENT_YEAR + "-09-22"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.startYear").value(CURRENT_YEAR))
        .andExpect(jsonPath("$.semesters.semester1Start").value(CURRENT_YEAR + "-09-22"))
        .andExpect(jsonPath("$.semesters.semester2End").value((CURRENT_YEAR + 1) + "-07-07"));

    // Assert
    assertEquals(LocalDate.of(CURRENT_YEAR, 9, 22), semester1StartInDatabase());
    mockMvc.perform(get("/academic-years/").session(staff))
        .andExpect(jsonPath("$[0].startYear").value(CURRENT_YEAR))
        .andExpect(jsonPath("$[0].semesters.semester1Start").value(CURRENT_YEAR + "-09-22"))
        .andExpect(jsonPath("$[1].semesters").doesNotExist());
  }

  @Test
  @DisplayName("A date before 1 September is 400 SEMESTER_OUTSIDE_YEAR with the year's limits, and nothing changes")
  void outsideYear() throws Exception {
    // Act
    edit(staff, CURRENT_YEAR, dates(CURRENT_YEAR, CURRENT_YEAR + "-08-31"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("SEMESTER_OUTSIDE_YEAR"))
        .andExpect(jsonPath("$.params.from").value(CURRENT_YEAR + "-09-01"))
        .andExpect(jsonPath("$.params.to").value((CURRENT_YEAR + 1) + "-08-31"));

    // Assert
    assertEquals(LocalDate.of(CURRENT_YEAR, 9, 15), semester1StartInDatabase());
  }

  @Test
  @DisplayName("Semester 1 ending before it starts is 400 SEMESTER_ENDS_BEFORE_START naming it")
  void endsBeforeStart() throws Exception {
    edit(staff, CURRENT_YEAR, dates(CURRENT_YEAR, (CURRENT_YEAR + 1) + "-02-08"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("SEMESTER_ENDS_BEFORE_START"))
        .andExpect(jsonPath("$.params.semester").value(1));
  }

  @Test
  @DisplayName("A date that isn't a date is a field error on it")
  void notADate() throws Exception {
    edit(staff, CURRENT_YEAR, dates(CURRENT_YEAR, "15/09/" + CURRENT_YEAR))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.fieldErrors[0].field").value("semester1Start"));
  }

  @Test
  @DisplayName("An older year's dates are 409 ACADEMIC_YEAR_NOT_CURRENT and stay empty")
  void olderYear() throws Exception {
    // Act
    edit(staff, CURRENT_YEAR - 1, dates(CURRENT_YEAR - 1, (CURRENT_YEAR - 1) + "-09-15"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("ACADEMIC_YEAR_NOT_CURRENT"));

    // Assert
    assertFalse(academicYearRepository.findById(CURRENT_YEAR - 1).orElseThrow().hasSemesters());
  }

  @Test
  @DisplayName("A year that isn't registered is 404")
  void unknownYear() throws Exception {
    edit(staff, CURRENT_YEAR + 50, dates(CURRENT_YEAR + 50, (CURRENT_YEAR + 50) + "-09-15"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
  }

  @Test
  @DisplayName("A teacher can't change the dates (403 ACCESS_DENIED)")
  void teacherIsDenied() throws Exception {
    // Arrange
    Teacher teacher = new Teacher("it.semesters.teacher@example.com", "it.semesters.teacher@studymatch.com",
        "Test Teacher", passwordEncoder.encode(PASSWORD));
    teacher.setMustChangePassword(false);
    teacherRepository.save(teacher);
    MockHttpSession session = loginAs("it.semesters.teacher@studymatch.com");

    // Act + Assert
    edit(session, CURRENT_YEAR, dates(CURRENT_YEAR, CURRENT_YEAR + "-09-22"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    assertEquals(LocalDate.of(CURRENT_YEAR, 9, 15), semester1StartInDatabase());
  }

}
