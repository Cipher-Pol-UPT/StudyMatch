package com.studymatch.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.DayOfWeek;
import java.time.LocalTime;

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
import com.studymatch.models.UnavailableSlot;
import com.studymatch.repositories.AcademicYearRepository;
import com.studymatch.repositories.StaffRepository;
import com.studymatch.repositories.TeacherRepository;
import com.studymatch.repositories.UnavailableSlotRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TeacherUnavailabilityIntegrationTest {

  private static final String PASSWORD = "Test1234";
  private static final String PATH = "/teachers/me/unavailability";
  // far in the future so it's the current year while the test runs
  private static final int PAST_YEAR = 2096;
  private static final int CURRENT_YEAR = 2097;

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private TeacherRepository teacherRepository;

  @Autowired
  private StaffRepository staffRepository;

  @Autowired
  private AcademicYearRepository academicYearRepository;

  @Autowired
  private UnavailableSlotRepository unavailableSlotRepository;

  @Autowired
  private PasswordEncoder passwordEncoder;

  private MockHttpSession ana;
  private MockHttpSession staff;

  // ana marked Friday 8:00 last year, rui marked Monday 8:00 this year. ana has nothing this year yet
  @BeforeEach
  void setUp() throws Exception {
    AcademicYear pastYear = academicYearRepository.save(new AcademicYear(PAST_YEAR));
    AcademicYear currentYear = academicYearRepository.save(new AcademicYear(CURRENT_YEAR));
    Teacher anaTeacher = teacherRepository.save(teacher("ana", "IT Slots Ana"));
    Teacher ruiTeacher = teacherRepository.save(teacher("rui", "IT Slots Rui"));
    unavailableSlotRepository.save(new UnavailableSlot(anaTeacher, pastYear, DayOfWeek.FRIDAY, LocalTime.of(8, 0)));
    unavailableSlotRepository.save(new UnavailableSlot(ruiTeacher, currentYear, DayOfWeek.MONDAY, LocalTime.of(8, 0)));

    Staff member = new Staff("it.slots.staff@example.com", "it.slots.staff@studymatch.com", "IT Slots Staff",
        passwordEncoder.encode(PASSWORD));
    member.setMustChangePassword(false);
    staffRepository.save(member);

    ana = loginAs("it.slots.ana@studymatch.com");
    staff = loginAs("it.slots.staff@studymatch.com");
  }

  private Teacher teacher(String login, String name) {
    Teacher teacher = new Teacher("it.slots." + login + "@example.com", "it.slots." + login + "@studymatch.com",
        name, passwordEncoder.encode(PASSWORD));
    teacher.setMustChangePassword(false);
    return teacher;
  }

  private MockHttpSession loginAs(String email) throws Exception {
    MvcResult result = mockMvc.perform(post("/users/login").with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
        .andReturn();
    assertEquals(200, result.getResponse().getStatus());
    return (MockHttpSession) result.getRequest().getSession(false);
  }

  private ResultActions save(MockHttpSession session, String slots) throws Exception {
    return mockMvc.perform(put(PATH).with(csrf()).session(session)
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"slots\":[" + slots + "]}"));
  }

  private ResultActions read(MockHttpSession session) throws Exception {
    return mockMvc.perform(get(PATH).session(session));
  }

  private static String slot(String day, String start) {
    return "{\"day\":\"" + day + "\",\"start\":\"" + start + "\"}";
  }

  @Test
  @DisplayName("A teacher with nothing marked this year gets an empty list, not last year's or another teacher's")
  void nothingMarked() throws Exception {
    // Act + Assert
    read(ana)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
  }

  @Test
  @DisplayName("Saving slots answers with them by day and time, and reading them back gives the same")
  void saveSlots() throws Exception {
    // Act
    save(ana, slot("WEDNESDAY", "19:30") + "," + slot("MONDAY", "09:00") + "," + slot("MONDAY", "08:30"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(3))
        .andExpect(jsonPath("$[0].day").value("MONDAY"))
        .andExpect(jsonPath("$[0].start").value("08:30"))
        .andExpect(jsonPath("$[1].start").value("09:00"))
        .andExpect(jsonPath("$[2].day").value("WEDNESDAY"))
        .andExpect(jsonPath("$[2].start").value("19:30"));

    // Assert
    read(ana)
        .andExpect(jsonPath("$.length()").value(3))
        .andExpect(jsonPath("$[0].start").value("08:30"))
        .andExpect(jsonPath("$[2].day").value("WEDNESDAY"));
  }

  @Test
  @DisplayName("Saving again replaces the old slots instead of adding to them")
  void saveReplaces() throws Exception {
    // Arrange
    save(ana, slot("MONDAY", "08:00") + "," + slot("TUESDAY", "10:00")).andExpect(status().isOk());

    // Act
    save(ana, slot("TUESDAY", "10:00") + "," + slot("THURSDAY", "15:30")).andExpect(status().isOk());

    // Assert
    read(ana)
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[0].day").value("TUESDAY"))
        .andExpect(jsonPath("$[1].day").value("THURSDAY"));
  }

  @Test
  @DisplayName("The same slot sent twice is saved once")
  void duplicateSlot() throws Exception {
    // Act
    save(ana, slot("MONDAY", "08:00") + "," + slot("MONDAY", "08:00"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1));

    // Assert
    read(ana).andExpect(jsonPath("$.length()").value(1));
  }

  @Test
  @DisplayName("Saving an empty list clears this year's slots (boundary)")
  void clearSlots() throws Exception {
    // Arrange
    save(ana, slot("MONDAY", "08:00")).andExpect(status().isOk());

    // Act
    save(ana, "")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));

    // Assert
    read(ana).andExpect(jsonPath("$.length()").value(0));
  }

  @Test
  @DisplayName("A teacher's slots don't touch another teacher's")
  void otherTeacherKeepsSlots() throws Exception {
    // Act
    save(ana, slot("TUESDAY", "08:00")).andExpect(status().isOk());

    // Assert
    read(loginAs("it.slots.rui@studymatch.com"))
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].day").value("MONDAY"));
  }

  @Test
  @DisplayName("One slot off the grid is 400 INVALID_SLOT and the old slots stay")
  void slotOffGrid() throws Exception {
    // Arrange
    save(ana, slot("MONDAY", "08:00")).andExpect(status().isOk());

    // Act
    save(ana, slot("TUESDAY", "09:00") + "," + slot("SATURDAY", "09:00"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_SLOT"));
    save(ana, slot("TUESDAY", "08:15"))
        .andExpect(jsonPath("$.code").value("INVALID_SLOT"));
    save(ana, slot("TUESDAY", "20:00"))
        .andExpect(jsonPath("$.code").value("INVALID_SLOT"));

    // Assert
    read(ana)
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].day").value("MONDAY"));
  }

  @Test
  @DisplayName("A slot without a day, or with a day that doesn't exist, is 400 VALIDATION_FAILED")
  void badDay() throws Exception {
    // Act + Assert
    save(ana, "{\"start\":\"08:00\"}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    save(ana, slot("FUNDAY", "08:00"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
  }

  @Test
  @DisplayName("No slots list at all is 400 VALIDATION_FAILED, it isn't read as clearing them")
  void missingList() throws Exception {
    // Act + Assert
    mockMvc.perform(put(PATH).with(csrf()).session(ana).contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.fieldErrors[0].field").value("slots"));
  }

  @Test
  @DisplayName("Staff can't read or save teacher unavailability: 403")
  void staffForbidden() throws Exception {
    // Act + Assert
    read(staff)
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    save(staff, slot("MONDAY", "08:00")).andExpect(status().isForbidden());
  }

}
