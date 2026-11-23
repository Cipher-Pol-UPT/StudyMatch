package com.studymatch.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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

import com.studymatch.models.CustomChat;
import com.studymatch.models.Degree;
import com.studymatch.models.DirectChat;
import com.studymatch.models.PersonalityProfile;
import com.studymatch.models.Staff;
import com.studymatch.models.Student;
import com.studymatch.repositories.ChatRepository;
import com.studymatch.repositories.DegreeRepository;
import com.studymatch.repositories.StaffRepository;
import com.studymatch.repositories.StudentRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class EditGroupChatIntegrationTest {

  private static final String PASSWORD = "Test1234";

  // the 8 bytes every PNG starts with
  private static final String PICTURE = "data:image/png;base64,iVBORw0KGgo=";

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private DegreeRepository degreeRepository;

  @Autowired
  private StudentRepository studentRepository;

  @Autowired
  private StaffRepository staffRepository;

  @Autowired
  private ChatRepository chatRepository;

  @Autowired
  private PasswordEncoder passwordEncoder;

  private String passwordHash;
  private Degree degree;
  private Student ana;
  private Student rui;
  private Student eva;
  private CustomChat group;
  private MockHttpSession anaSession;
  private MockHttpSession ruiSession;

  // ana owns a group chat with rui and eva (rolled back after each test)
  @BeforeEach
  void setUp() throws Exception {
    passwordHash = passwordEncoder.encode(PASSWORD);
    degree = degreeRepository.save(new Degree("IT Edit Chat Degree", 3));
    ana = saveStudent("it.editchat.ana@example.com", "Ana Edit Test");
    rui = saveStudent("it.editchat.rui@example.com", "Rui Edit Test");
    eva = saveStudent("it.editchat.eva@example.com", "Eva Edit Test");
    group = chatRepository.save(new CustomChat("Study group", null, ana, List.of(rui, eva)));
    anaSession = loginAs(ana.getInstitutionalEmail());
    ruiSession = loginAs(rui.getInstitutionalEmail());
  }

  private Student saveStudent(String personalEmail, String name) {
    Student student = new Student(personalEmail, name, passwordHash, degree, 1);
    student.setMustChangePassword(false);
    student.setPersonalityProfile(new PersonalityProfile(0.5, 0.5, 0.5, 0.5, 0.5));
    student = studentRepository.save(student);
    student.generateInstitutionalEmail("studymatch.com");
    return studentRepository.save(student);
  }

  private MockHttpSession loginAs(String email) throws Exception {
    MvcResult result = mockMvc.perform(post("/users/login").with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
        .andReturn();
    assertEquals(200, result.getResponse().getStatus());
    return (MockHttpSession) result.getRequest().getSession(false);
  }

  private ResultActions rename(MockHttpSession session, Long chatId, String nameJson) throws Exception {
    return mockMvc.perform(put("/chats/" + chatId + "/name").with(csrf()).session(session)
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"name\":" + nameJson + "}"));
  }

  private ResultActions changePicture(MockHttpSession session, Long chatId, String pictureJson) throws Exception {
    return mockMvc.perform(put("/chats/" + chatId + "/picture").with(csrf()).session(session)
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"picture\":" + pictureJson + "}"));
  }

  // renaming

  @Test
  @DisplayName("A member who isn't the owner renames it, and the others see the new name in their list")
  void memberRenames() throws Exception {
    // Act
    rename(ruiSession, group.getId(), "\"  Exam prep  \"").andExpect(status().isNoContent());

    // Assert - stripped like creating
    mockMvc.perform(get("/chats/").session(loginAs(eva.getInstitutionalEmail())))
        .andExpect(jsonPath("$[0].name").value("Exam prep"));
  }

  @Test
  @DisplayName("A name over 50 characters is refused")
  void nameTooLong() throws Exception {
    // Act + Assert
    rename(ruiSession, group.getId(), "\"" + "a".repeat(51) + "\"")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
  }

  @Test
  @DisplayName("A blank name is refused")
  void blankName() throws Exception {
    // Act + Assert
    rename(ruiSession, group.getId(), "\"   \"")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
  }

  @Test
  @DisplayName("A DM has no name to change: NOT_FOUND")
  void directChatRename() throws Exception {
    // Arrange
    DirectChat direct = chatRepository.save(new DirectChat(ana, rui));

    // Act + Assert
    rename(anaSession, direct.getId(), "\"Exam prep\"").andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("Someone who isn't in the group chat can't rename it: NOT_FOUND (GR8)")
  void nonMemberRename() throws Exception {
    // Arrange
    Student zeca = saveStudent("it.editchat.zeca@example.com", "Zeca Edit Test");

    // Act + Assert
    rename(loginAs(zeca.getInstitutionalEmail()), group.getId(), "\"Exam prep\"").andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("Staff can't rename a chat: 403")
  void staffRename() throws Exception {
    // Act + Assert
    rename(staffSession(), group.getId(), "\"Exam prep\"").andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("Renaming without logging in is 401")
  void renameNotLoggedIn() throws Exception {
    // Act + Assert
    mockMvc.perform(put("/chats/" + group.getId() + "/name").with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"name\":\"Exam prep\"}"))
        .andExpect(status().isUnauthorized());
  }

  // the picture

  @Test
  @DisplayName("The owner changes the picture, the list says there is one and members get the image")
  void ownerChangesPicture() throws Exception {
    // Act
    changePicture(anaSession, group.getId(), "\"" + PICTURE + "\"").andExpect(status().isNoContent());

    // Assert
    mockMvc.perform(get("/chats/").session(ruiSession))
        .andExpect(jsonPath("$[0].picture").value(true));
    mockMvc.perform(get("/chats/" + group.getId() + "/picture").session(ruiSession))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.IMAGE_PNG));
  }

  @Test
  @DisplayName("The owner sending null takes the picture off")
  void ownerRemovesPicture() throws Exception {
    // Arrange
    changePicture(anaSession, group.getId(), "\"" + PICTURE + "\"").andExpect(status().isNoContent());

    // Act
    changePicture(anaSession, group.getId(), "null").andExpect(status().isNoContent());

    // Assert
    mockMvc.perform(get("/chats/" + group.getId() + "/picture").session(ruiSession))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("A member who isn't the owner can't change the picture: NOT_CHAT_OWNER")
  void notOwnerPicture() throws Exception {
    // Act + Assert
    changePicture(ruiSession, group.getId(), "\"" + PICTURE + "\"")
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("NOT_CHAT_OWNER"));
  }

  @Test
  @DisplayName("A picture that isn't a PNG or JPEG is refused: INVALID_GROUP_CHAT_PICTURE")
  void invalidPicture() throws Exception {
    // Act + Assert
    changePicture(anaSession, group.getId(), "\"data:image/png;base64,aGVsbG8=\"")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_GROUP_CHAT_PICTURE"));
  }

  @Test
  @DisplayName("A DM has no picture of its own to change: NOT_FOUND")
  void directChatPicture() throws Exception {
    // Arrange
    DirectChat direct = chatRepository.save(new DirectChat(ana, rui));

    // Act + Assert
    changePicture(anaSession, direct.getId(), "\"" + PICTURE + "\"").andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("Staff can't change a chat's picture: 403")
  void staffPicture() throws Exception {
    // Act + Assert
    changePicture(staffSession(), group.getId(), "null").andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("Changing the picture without logging in is 401")
  void pictureNotLoggedIn() throws Exception {
    // Act + Assert
    mockMvc.perform(put("/chats/" + group.getId() + "/picture").with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"picture\":null}"))
        .andExpect(status().isUnauthorized());
  }

  private MockHttpSession staffSession() throws Exception {
    Staff staff = new Staff("it.editchat.staff@example.com", "it.editchat.staff@studymatch.com",
        "Staff Edit Test", passwordHash);
    staff.setMustChangePassword(false);
    staffRepository.save(staff);
    return loginAs(staff.getInstitutionalEmail());
  }

}
