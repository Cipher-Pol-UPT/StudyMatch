package com.studymatch.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.studymatch.models.ModelTestData;
import com.studymatch.models.TutorRole;
import com.studymatch.repositories.RatingRepository;

class RatingServiceTest {

  private RatingRepository ratingRepository;
  private RatingService ratingService;

  private TutorRole tutorRole;

  @BeforeEach
  void setUp() {
    ratingRepository = mock(RatingRepository.class);
    ratingService = new RatingService(ratingRepository);

    tutorRole = ModelTestData.tutorRole(ModelTestData.student("Test Tutor"));
  }

  @Test
  @DisplayName("The average rating comes from the tutor's ratings")
  void averageRatingOfTheTutor() {
    // Arrange
    when(ratingRepository.findAverageStarsByTutorRole(tutorRole)).thenReturn(4.5);

    // Act
    Double average = ratingService.getAverageStars(tutorRole);

    // Assert
    assertEquals(4.5, average);
  }

  @Test
  @DisplayName("A tutor without ratings has no average")
  void tutorWithoutRatingsHasNoAverage() {
    // Arrange
    when(ratingRepository.findAverageStarsByTutorRole(tutorRole)).thenReturn(null);

    // Act
    Double average = ratingService.getAverageStars(tutorRole);

    // Assert
    assertNull(average);
  }

  @Test
  @DisplayName("Counts the tutor's ratings")
  void countsTheTutorsRatings() {
    // Arrange
    when(ratingRepository.countByParticipation_StudyRoom_TutorRole(tutorRole)).thenReturn(7L);

    // Act
    long ratings = ratingService.countRatings(tutorRole);

    // Assert
    assertEquals(7, ratings);
  }

}
