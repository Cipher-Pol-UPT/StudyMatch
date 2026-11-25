package com.studymatch.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

import com.studymatch.enums.NoteColour;
import com.studymatch.models.Degree;
import com.studymatch.models.Note;
import com.studymatch.models.PersonalityProfile;
import com.studymatch.models.Staff;
import com.studymatch.models.Student;
import com.studymatch.repositories.DegreeRepository;
import com.studymatch.repositories.NoteRepository;
import com.studymatch.repositories.StaffRepository;
import com.studymatch.repositories.StudentRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class NoteIntegrationTest {

  private static final String PASSWORD = "Test1234";

  @Autowired
  private MockMvc mockMvc;
  @Autowired
  private PasswordEncoder passwordEncoder;
  @Autowired
  private DegreeRepository degreeRepository;
  @Autowired
  private StudentRepository studentRepository;
  @Autowired
  private StaffRepository staffRepository;
  @Autowired
  private NoteRepository noteRepository;

  private Student ana;
  private Student rui;
  private MockHttpSession session;

  @BeforeEach
  void setUp() throws Exception {
    ana = saveStudent("it.notes.ana@example.com", "Ana Test");
    rui = saveStudent("it.notes.rui@example.com", "Rui Test");
    session = login(ana.getInstitutionalEmail());
  }

  private Student saveStudent(String personalEmail, String name) {
    Degree degree = degreeRepository.save(new Degree("IT Notes Degree " + name, 3));
    Student student = new Student(personalEmail, name, passwordEncoder.encode(PASSWORD), degree, 1);
    student.setMustChangePassword(false);
    student.setPersonalityProfile(new PersonalityProfile(0.5, 0.5, 0.5, 0.5, 0.5));
    student = studentRepository.save(student);
    student.generateInstitutionalEmail("studymatch.com");
    return studentRepository.saveAndFlush(student);
  }

  private MockHttpSession login(String email) throws Exception {
    MvcResult result = mockMvc.perform(post("/users/login").with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
        .andReturn();
    assertEquals(200, result.getResponse().getStatus());
    return (MockHttpSession) result.getRequest().getSession(false);
  }

  private ResultActions create(MockHttpSession as, String body) throws Exception {
    return mockMvc.perform(post("/notes/").with(csrf()).session(as)
        .contentType(MediaType.APPLICATION_JSON).content(body));
  }

  private ResultActions change(Long id, String body) throws Exception {
    return mockMvc.perform(patch("/notes/" + id).with(csrf()).session(session)
        .contentType(MediaType.APPLICATION_JSON).content(body));
  }

  @Test
  @DisplayName("POST /notes/ adds a note and answers 201 with it")
  void createNote() throws Exception {
    // Act + Assert
    create(session, "{\"text\":\"  buy pens  \",\"colour\":\"PINK\",\"x\":12.5,\"y\":40}")
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.text").value("buy pens"))
        .andExpect(jsonPath("$.colour").value("PINK"))
        .andExpect(jsonPath("$.x").value(12.5))
        .andExpect(jsonPath("$.y").value(40.0));
    assertEquals(1, noteRepository.countByOwner_Number(ana.getNumber()));
  }

  @Test
  @DisplayName("POST /notes/ with 501 characters is NOTE_TOO_LONG")
  void createTooLong() throws Exception {
    // Act + Assert
    create(session, "{\"text\":\"" + "a".repeat(501) + "\",\"colour\":\"YELLOW\",\"x\":0,\"y\":0}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("NOTE_TOO_LONG"));
  }

  @Test
  @DisplayName("POST /notes/ off the board is INVALID_NOTE_POSITION")
  void createOffTheBoard() throws Exception {
    // Act + Assert
    create(session, "{\"text\":\"\",\"colour\":\"YELLOW\",\"x\":100.5,\"y\":0}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_NOTE_POSITION"));
  }

  @Test
  @DisplayName("POST /notes/ without a colour is a field error")
  void createWithoutColour() throws Exception {
    // Act + Assert
    create(session, "{\"text\":\"\",\"x\":0,\"y\":0}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors[0].field").value("colour"));
  }

  @Test
  @DisplayName("POST /notes/ with 20 notes already is NOTE_LIMIT_REACHED")
  void createOverTheLimit() throws Exception {
    // Arrange
    for (int i = 0; i < Note.MAX_NOTES; i++) {
      noteRepository.save(new Note(ana, "note " + i, NoteColour.YELLOW, i, i));
    }

    // Act + Assert
    create(session, "{\"text\":\"one more\",\"colour\":\"YELLOW\",\"x\":0,\"y\":0}")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("NOTE_LIMIT_REACHED"));
  }

  @Test
  @DisplayName("GET /notes/ gives only your notes, the last one changed at the end")
  void listOwnNotes() throws Exception {
    // Arrange
    Note first = noteRepository.save(new Note(ana, "first", NoteColour.YELLOW, 10, 10));
    noteRepository.save(new Note(ana, "second", NoteColour.BLUE, 20, 20));
    noteRepository.save(new Note(rui, "not yours", NoteColour.GREEN, 30, 30));
    Thread.sleep(5);
    change(first.getId(), "{\"x\":50,\"y\":50}").andExpect(status().isOk());

    // Act + Assert
    mockMvc.perform(get("/notes/").session(session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[0].text").value("second"))
        .andExpect(jsonPath("$[1].text").value("first"));
  }

  @Test
  @DisplayName("PATCH /notes/{id} changes only what's sent")
  void changeNote() throws Exception {
    // Arrange
    Note note = noteRepository.save(new Note(ana, "draft", NoteColour.YELLOW, 10, 20));

    // Act + Assert
    change(note.getId(), "{\"text\":\"final\",\"colour\":\"GREEN\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.text").value("final"))
        .andExpect(jsonPath("$.colour").value("GREEN"))
        .andExpect(jsonPath("$.x").value(10.0))
        .andExpect(jsonPath("$.y").value(20.0));
  }

  @Test
  @DisplayName("PATCH /notes/{id} off the board is INVALID_NOTE_POSITION and leaves it where it was")
  void moveOffTheBoard() throws Exception {
    // Arrange
    Note note = noteRepository.save(new Note(ana, "stay", NoteColour.YELLOW, 10, 20));

    // Act
    change(note.getId(), "{\"x\":-1,\"y\":20}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_NOTE_POSITION"));

    // Assert
    assertEquals(10, noteRepository.findById(note.getId()).orElseThrow().getX());
  }

  @Test
  @DisplayName("PATCH and DELETE on someone else's note are NOT_FOUND, and it stays the same")
  void someoneElsesNote() throws Exception {
    // Arrange
    Note theirs = noteRepository.save(new Note(rui, "rui's", NoteColour.BLUE, 10, 20));

    // Act + Assert
    change(theirs.getId(), "{\"text\":\"mine now\"}")
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    mockMvc.perform(delete("/notes/" + theirs.getId()).with(csrf()).session(session))
        .andExpect(status().isNotFound());
    assertEquals("rui's", noteRepository.findById(theirs.getId()).orElseThrow().getText());
  }

  @Test
  @DisplayName("DELETE /notes/{id} removes it for good")
  void deleteNote() throws Exception {
    // Arrange
    Note note = noteRepository.save(new Note(ana, "bye", NoteColour.PINK, 10, 20));

    // Act
    mockMvc.perform(delete("/notes/" + note.getId()).with(csrf()).session(session))
        .andExpect(status().isNoContent());

    // Assert
    assertTrue(noteRepository.findById(note.getId()).isEmpty());
  }

  @Test
  @DisplayName("Staff have notes too, every role does")
  void staffNotes() throws Exception {
    // Arrange
    Staff staff = new Staff("it.notes.staff@example.com", "it.notes.staff@studymatch.com", "Staff Notes Test",
        passwordEncoder.encode(PASSWORD));
    staff.setMustChangePassword(false);
    staffRepository.saveAndFlush(staff);
    MockHttpSession staffSession = login(staff.getInstitutionalEmail());

    // Act + Assert
    create(staffSession, "{\"text\":\"staff note\",\"colour\":\"BLUE\",\"x\":5,\"y\":5}")
        .andExpect(status().isCreated());
    mockMvc.perform(get("/notes/").session(staffSession))
        .andExpect(jsonPath("$.length()").value(1));
  }

  @Test
  @DisplayName("Without logging in it's 401")
  void notLoggedIn() throws Exception {
    // Act + Assert
    mockMvc.perform(get("/notes/")).andExpect(status().isUnauthorized());
  }
}
