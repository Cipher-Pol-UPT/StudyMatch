package com.studymatch.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.studymatch.dtos.TutorBadgeDTO;
import com.studymatch.models.ModelTestData;
import com.studymatch.models.Student;
import com.studymatch.models.TutorRole;
import com.studymatch.repositories.TutorRoleRepository;

class TutorRoleServiceTest {

  private TutorRoleRepository tutorRoleRepository;
  private RatingService ratingService;
  private TutorRoleService tutorRoleService;

  private Student tutor;

  @BeforeEach
  void setUp() {
    tutorRoleRepository = mock(TutorRoleRepository.class);
    ratingService = mock(RatingService.class);
    tutorRoleService = new TutorRoleService(tutorRoleRepository, ratingService);

    tutor = ModelTestData.student("Test Tutor");
  }

  @Test
  @DisplayName("A tutor badge shows the subject, the average rating and the number of ratings")
  void tutorBadgeShowsSubjectAndRating() {
    // Arrange
    TutorRole tutorRole = ModelTestData.tutorRole(tutor);
    when(tutorRoleRepository.findByStudent(tutor)).thenReturn(List.of(tutorRole));
    when(ratingService.getAverageStars(tutorRole)).thenReturn(4.0);
    when(ratingService.countRatings(tutorRole)).thenReturn(3L);

    // Act
    List<TutorBadgeDTO> badges = tutorRoleService.getTutorBadges(tutor);

    // Assert
    assertEquals(1, badges.size());
    assertEquals("Test Subject", badges.get(0).subject());
    assertEquals(4.0, badges.get(0).averageRating());
    assertEquals(3, badges.get(0).ratingCount());
  }

  @Test
  @DisplayName("A tutor badge without ratings has no average")
  void tutorBadgeWithoutRatingsHasNoAverage() {
    // Arrange
    TutorRole tutorRole = ModelTestData.tutorRole(tutor);
    when(tutorRoleRepository.findByStudent(tutor)).thenReturn(List.of(tutorRole));
    when(ratingService.getAverageStars(tutorRole)).thenReturn(null);
    when(ratingService.countRatings(tutorRole)).thenReturn(0L);

    // Act
    TutorBadgeDTO badge = tutorRoleService.getTutorBadges(tutor).get(0);

    // Assert
    assertNull(badge.averageRating());
    assertEquals(0, badge.ratingCount());
  }

  @Test
  @DisplayName("A student who isn't a tutor has no tutor badges")
  void studentWithoutTutorRolesHasNoBadges() {
    // Act
    List<TutorBadgeDTO> badges = tutorRoleService.getTutorBadges(tutor);

    // Assert
    assertTrue(badges.isEmpty());
  }

}
