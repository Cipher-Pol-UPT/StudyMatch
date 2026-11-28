package com.studymatch.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;

import com.studymatch.dtos.DegreePlacementResponseDTO;
import com.studymatch.dtos.SubjectResponseDTO;
import com.studymatch.dtos.UpdateSubjectRequestDTO;
import com.studymatch.models.Category;
import com.studymatch.models.Degree;
import com.studymatch.models.Subject;
import com.studymatch.repositories.CategoryRepository;
import com.studymatch.repositories.DegreeRepository;
import com.studymatch.repositories.SubjectRepository;

// not @Transactional on purpose: open-in-view is off, so the controller maps the response after the session
// closes, like a real request. A test transaction would keep it open and hide anything the reads don't load (#391)
@SpringBootTest
@ActiveProfiles("test")
@WithMockUser(roles = "STAFF")
class SubjectLazyLoadingIntegrationTest {

  @Autowired
  private SubjectController subjectController;

  @Autowired
  private SubjectRepository subjectRepository;

  @Autowired
  private CategoryRepository categoryRepository;

  @Autowired
  private DegreeRepository degreeRepository;

  private Category category;
  private Degree degree;
  private Subject subject;

  @BeforeEach
  void setUp() {
    category = categoryRepository.save(new Category("IT Lazy Category"));
    degree = degreeRepository.save(new Degree("IT Lazy Degree", 3));
    Subject toSave = new Subject("ITLAZY1", "IT Lazy Subject", 6, category);
    toSave.addDegree(degree, 2, 1);
    subject = subjectRepository.save(toSave);
  }

  @AfterEach
  void cleanUp() {
    subjectRepository.deleteById(subject.getId());
    degreeRepository.delete(degree);
    categoryRepository.delete(category);
  }

  private static void assertFullSubject(SubjectResponseDTO response, String name) {
    assertEquals(name, response.name());
    assertEquals("IT Lazy Category", response.category().name());
    DegreePlacementResponseDTO placement = response.degrees().get(0);
    assertEquals("IT Lazy Degree", placement.degreeName());
    assertEquals(2, placement.curricularYear());
  }

  @Test
  @DisplayName("The subject list answers with each subject's category and degrees")
  void listLoadsCategoryAndDegrees() {
    // Act
    List<SubjectResponseDTO> subjects = subjectController.listSubjects().getBody();

    // Assert
    assertFullSubject(subjects.stream().filter(s -> s.code().equals("ITLAZY1")).findFirst().orElseThrow(),
        "IT Lazy Subject");
  }

  @Test
  @DisplayName("One subject answers with its category and degrees")
  void getLoadsCategoryAndDegrees() {
    // Act
    ResponseEntity<SubjectResponseDTO> response = subjectController.getSubject(subject.getId());

    // Assert
    assertEquals(200, response.getStatusCode().value());
    assertFullSubject(response.getBody(), "IT Lazy Subject");
  }

  @Test
  @DisplayName("Updating a subject answers with its category and degrees")
  void updateLoadsCategoryAndDegrees() {
    // Act
    ResponseEntity<SubjectResponseDTO> response = subjectController.updateSubject(subject.getId(),
        new UpdateSubjectRequestDTO("IT Lazy Renamed", 6, null));

    // Assert
    assertEquals(200, response.getStatusCode().value());
    assertFullSubject(response.getBody(), "IT Lazy Renamed");
  }

}
