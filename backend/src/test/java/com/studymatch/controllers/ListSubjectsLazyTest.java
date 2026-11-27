package com.studymatch.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.studymatch.dtos.SubjectResponseDTO;
import com.studymatch.models.Category;
import com.studymatch.models.Degree;
import com.studymatch.models.Subject;
import com.studymatch.repositories.CategoryRepository;
import com.studymatch.repositories.DegreeRepository;
import com.studymatch.repositories.SubjectRepository;
import com.studymatch.services.SubjectService;

// open-in-view is off, so the controller builds the response after the service returns. This test has no
// @Transactional on purpose: it fails if the query didn't load everything the response reads (degrees,
// the degree each placement points at and the category), which the transactional tests would hide.
@SpringBootTest
@ActiveProfiles("test")
class ListSubjectsLazyTest {

  private static final String CODE = "LAZYSUB";
  private static final String NAME = "Lazy Subject";
  private static final String DEGREE = "Lazy Subject Degree";
  private static final String CATEGORY = "Lazy Subject Category";

  @Autowired
  private SubjectService subjectService;

  @Autowired
  private SubjectRepository subjectRepository;

  @Autowired
  private DegreeRepository degreeRepository;

  @Autowired
  private CategoryRepository categoryRepository;

  @BeforeEach
  void seed() {
    removeSeeded();
    Category category = categoryRepository.save(new Category(CATEGORY));
    Degree degree = degreeRepository.save(new Degree(DEGREE, 3));
    Subject subject = new Subject(CODE, NAME, 6, category);
    subject.addDegree(degree, 1, 1);
    subjectRepository.save(subject);
  }

  @AfterEach
  void removeSeeded() {
    // the subject cascades to its degree placements, so the degree and category are free after it
    subjectRepository.findByCodeIgnoreCase(CODE).ifPresent(subjectRepository::delete);
    degreeRepository.findAll().stream().filter(degree -> degree.getName().equals(DEGREE))
        .forEach(degreeRepository::delete);
    categoryRepository.findAll().stream().filter(category -> category.getName().equals(CATEGORY))
        .forEach(categoryRepository::delete);
  }

  @Test
  @DisplayName("Listing and mapping the subjects doesn't need the session opened by the service")
  void listSubjectsMapsOutsideTheService() {
    // Act
    List<Subject> subjects = subjectService.listSubjects();
    List<SubjectResponseDTO> dtos = subjects.stream().map(SubjectResponseDTO::from).toList();

    // Assert
    SubjectResponseDTO seeded = dtos.stream().filter(dto -> dto.code().equals(CODE)).findFirst().orElseThrow();
    assertEquals(CATEGORY, seeded.category().name());
    assertTrue(seeded.degrees().stream().anyMatch(placement -> placement.degreeName().equals(DEGREE)));
  }
}
