package com.studymatch.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

import com.jayway.jsonpath.JsonPath;
import com.studymatch.enums.RoomType;
import com.studymatch.models.Room;
import com.studymatch.models.Staff;
import com.studymatch.models.Teacher;
import com.studymatch.repositories.RoomRepository;
import com.studymatch.repositories.StaffRepository;
import com.studymatch.repositories.TeacherRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ManageRoomsIntegrationTest {

  private static final String PASSWORD = "Test1234";
  private static final String BUILDING = "IT Building";

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private StaffRepository staffRepository;

  @Autowired
  private TeacherRepository teacherRepository;

  @Autowired
  private RoomRepository roomRepository;

  @Autowired
  private PasswordEncoder passwordEncoder;

  // IT.101 at (20,0) 20 x 20 on floor 1
  private Room existing;

  @BeforeEach
  void setUp() {
    existing = roomRepository.save(new Room("IT.101", BUILDING, 1, RoomType.CLASSROOM, 30, 20, 0, 20, 20));
  }

  private MockHttpSession loginAsStaff() throws Exception {
    Staff staff = new Staff("it.staff@example.com", "it.staff@studymatch.com", "Test Staff", passwordEncoder.encode(PASSWORD));
    staff.setMustChangePassword(false);
    staffRepository.save(staff);
    return loginAs("it.staff@studymatch.com");
  }

  private MockHttpSession loginAsTeacher() throws Exception {
    Teacher teacher = new Teacher("it.teacher@example.com", "it.teacher@studymatch.com", "Test Teacher",
        passwordEncoder.encode(PASSWORD));
    teacher.setMustChangePassword(false);
    teacherRepository.save(teacher);
    return loginAs("it.teacher@studymatch.com");
  }

  private MockHttpSession loginAs(String email) throws Exception {
    MvcResult result = mockMvc.perform(post("/users/login").with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
        .andReturn();
    assertEquals(200, result.getResponse().getStatus());
    return (MockHttpSession) result.getRequest().getSession(false);
  }

  private static String body(String code, String type, int x, int y, int width, int depth) {
    return "{\"code\":\"" + code + "\",\"building\":\"" + BUILDING + "\",\"floor\":1,\"type\":\"" + type
        + "\",\"capacity\":30,\"x\":" + x + ",\"y\":" + y + ",\"width\":" + width + ",\"depth\":" + depth + "}";
  }

  private ResultActions register(MockHttpSession session, String body) throws Exception {
    return mockMvc.perform(post("/rooms/").with(csrf()).session(session)
        .contentType(MediaType.APPLICATION_JSON)
        .content(body));
  }

  private ResultActions edit(MockHttpSession session, Long id, String body) throws Exception {
    return mockMvc.perform(put("/rooms/" + id).with(csrf()).session(session)
        .contentType(MediaType.APPLICATION_JSON)
        .content(body));
  }

  private ResultActions setActive(MockHttpSession session, Long id, boolean active) throws Exception {
    return mockMvc.perform(put("/rooms/" + id + (active ? "/activate" : "/deactivate")).with(csrf()).session(session));
  }

  private boolean activeInDatabase(Long id) {
    return roomRepository.findById(id).orElseThrow().isActive();
  }

  // register room

  @Test
  @DisplayName("Staff registers a room next to another one: 201 with its data, active, and saved")
  void staffRegistersRoom() throws Exception {
    // Arrange
    MockHttpSession staff = loginAsStaff();

    // Act
    MvcResult result = register(staff, body(" IT.102 ", "LAB", 0, 0, 20, 20))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.code").value("IT.102"))
        .andExpect(jsonPath("$.building").value(BUILDING))
        .andExpect(jsonPath("$.floor").value(1))
        .andExpect(jsonPath("$.type").value("LAB"))
        .andExpect(jsonPath("$.capacity").value(30))
        .andExpect(jsonPath("$.width").value(20))
        .andExpect(jsonPath("$.active").value(true))
        .andReturn();

    // Assert
    Number id = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    assertEquals("IT.102", roomRepository.findById(id.longValue()).orElseThrow().getCode());
  }

  @Test
  @DisplayName("A code that exists, in another case, is 409 ROOM_ALREADY_EXISTS")
  void codeTaken() throws Exception {
    // Arrange
    MockHttpSession staff = loginAsStaff();

    // Act + Assert
    register(staff, body("it.101", "CLASSROOM", 0, 50, 10, 10))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("ROOM_ALREADY_EXISTS"));
  }

  @Test
  @DisplayName("A room over another active room on the floor is 409 ROOM_OVERLAP naming it")
  void overlap() throws Exception {
    // Arrange
    MockHttpSession staff = loginAsStaff();

    // Act + Assert
    register(staff, body("IT.102", "CLASSROOM", 10, 10, 20, 20))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("ROOM_OVERLAP"))
        .andExpect(jsonPath("$.params.code").value("IT.101"));
  }

  @Test
  @DisplayName("A room going past the edge of the grid is 400 ROOM_OUTSIDE_FLOOR with its size")
  void outsideFloor() throws Exception {
    // Arrange
    MockHttpSession staff = loginAsStaff();

    // Act + Assert
    register(staff, body("IT.102", "CLASSROOM", 90, 50, 11, 10))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("ROOM_OUTSIDE_FLOOR"))
        .andExpect(jsonPath("$.params.size").value(100));
  }

  @Test
  @DisplayName("A type that doesn't exist is a field error on type")
  void unknownType() throws Exception {
    // Arrange
    MockHttpSession staff = loginAsStaff();

    // Act + Assert
    register(staff, body("IT.102", "KITCHEN", 0, 50, 10, 10))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.fieldErrors[0].field").value("type"))
        .andExpect(jsonPath("$.fieldErrors[0].code").value("INVALID"));
  }

  // edit room

  @Test
  @DisplayName("Staff edits a room over part of its own old place: 200 and saved")
  void staffEditsRoom() throws Exception {
    // Arrange
    MockHttpSession staff = loginAsStaff();

    // Act
    edit(staff, existing.getId(), body("IT.101", "AUDITORIUM", 30, 0, 30, 30))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.type").value("AUDITORIUM"))
        .andExpect(jsonPath("$.x").value(30));

    // Assert
    Room saved = roomRepository.findById(existing.getId()).orElseThrow();
    assertEquals(RoomType.AUDITORIUM, saved.getType());
    assertEquals(30, saved.getDepth());
  }

  @Test
  @DisplayName("Editing onto another room is 409 ROOM_OVERLAP and nothing changes")
  void editOverlap() throws Exception {
    // Arrange
    Room other = roomRepository.save(new Room("IT.102", BUILDING, 1, RoomType.CLASSROOM, 30, 50, 0, 20, 20));
    MockHttpSession staff = loginAsStaff();

    // Act + Assert
    edit(staff, other.getId(), body("IT.102", "CLASSROOM", 30, 0, 20, 20))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("ROOM_OVERLAP"))
        .andExpect(jsonPath("$.params.code").value("IT.101"));
  }

  @Test
  @DisplayName("Editing a room that doesn't exist is 404")
  void editUnknownRoom() throws Exception {
    // Arrange
    MockHttpSession staff = loginAsStaff();

    // Act + Assert
    edit(staff, Long.MAX_VALUE, body("IT.102", "CLASSROOM", 0, 50, 10, 10))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
  }

  // deactivate and activate

  @Test
  @DisplayName("A deactivated room frees its place, and can't be activated again once another room takes it")
  void deactivateThenActivate() throws Exception {
    // Arrange
    MockHttpSession staff = loginAsStaff();

    // Act: deactivate it, then a new room goes in its place
    setActive(staff, existing.getId(), false)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.active").value(false));
    register(staff, body("IT.102", "CLASSROOM", 20, 0, 20, 20)).andExpect(status().isCreated());

    // Assert
    setActive(staff, existing.getId(), true)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("ROOM_OVERLAP"))
        .andExpect(jsonPath("$.params.code").value("IT.102"));
    assertFalse(activeInDatabase(existing.getId()));
  }

  @Test
  @DisplayName("A deactivated room with its place free is activated again")
  void activateAgain() throws Exception {
    // Arrange
    MockHttpSession staff = loginAsStaff();
    setActive(staff, existing.getId(), false).andExpect(status().isOk());

    // Act
    setActive(staff, existing.getId(), true)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.active").value(true));

    // Assert
    assertTrue(activeInDatabase(existing.getId()));
  }

  // who can do what

  @Test
  @DisplayName("A teacher sees the rooms, inactive ones included")
  void teacherListsRooms() throws Exception {
    // Arrange
    Room inactive = new Room("IT.102", BUILDING, 1, RoomType.LAB, 20, 50, 0, 10, 10);
    inactive.setActive(false);
    roomRepository.save(inactive);
    MockHttpSession teacher = loginAsTeacher();

    // Act + Assert
    mockMvc.perform(get("/rooms/").session(teacher))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.code == 'IT.101')].active").value(true))
        .andExpect(jsonPath("$[?(@.code == 'IT.102')].active").value(false));
    mockMvc.perform(get("/rooms/" + existing.getId()).session(teacher))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value("IT.101"));
  }

  @Test
  @DisplayName("A teacher can't register, edit or deactivate rooms (403 ACCESS_DENIED)")
  void teacherIsDenied() throws Exception {
    // Arrange
    MockHttpSession teacher = loginAsTeacher();

    // Act + Assert
    register(teacher, body("IT.102", "CLASSROOM", 0, 50, 10, 10))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    edit(teacher, existing.getId(), body("IT.101", "LAB", 20, 0, 20, 20))
        .andExpect(status().isForbidden());
    setActive(teacher, existing.getId(), false).andExpect(status().isForbidden());
    assertTrue(activeInDatabase(existing.getId()));
  }

  @Test
  @DisplayName("Without logging in it's 401")
  void notLoggedIn() throws Exception {
    mockMvc.perform(get("/rooms/")).andExpect(status().isUnauthorized());
  }

}
