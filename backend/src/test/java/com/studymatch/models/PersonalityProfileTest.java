package com.studymatch.models;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.studymatch.exceptions.ErrorCode;
import com.studymatch.exceptions.StudyMatchException;

class PersonalityProfileTest {

  private static final double DELTA = 1e-4;

  @Test
  @DisplayName("The worked example from the spec gives the expected scores")
  void scoresTheSpecExample() {
    // Arrange
    List<Integer> answers = List.of(4, 5, 2, 4, 5, 2, 2, 4, 3, 1, 4, 4, 3, 4, 5);

    // Act
    PersonalityProfile profile = PersonalityProfile.fromAnswers(answers);

    // Assert
    assertEquals(0.6417, profile.getLeadership(), DELTA);
    assertEquals(0.6333, profile.getProblemSolving(), DELTA);
    assertEquals(0.9375, profile.getCreativity(), DELTA);
    assertEquals(0.4333, profile.getExecution(), DELTA);
  }

  @Test
  @DisplayName("All neutral answers give 0.5 on every score")
  void neutralAnswersGiveMiddleScores() {
    // Arrange
    List<Integer> answers = Collections.nCopies(PersonalityProfile.QUESTION_COUNT, 3);

    // Act
    PersonalityProfile profile = PersonalityProfile.fromAnswers(answers);

    // Assert
    assertEquals(0.5, profile.getLeadership(), DELTA);
    assertEquals(0.5, profile.getProblemSolving(), DELTA);
    assertEquals(0.5, profile.getCreativity(), DELTA);
    assertEquals(0.5, profile.getExecution(), DELTA);
  }

  @Test
  @DisplayName("Reversed questions are flipped before averaging")
  void reversedItemsAreFlipped() {
    // Arrange - each trait has two direct items and one reversed, so all 5s give a mean of (5 + 5 + 1) / 3
    List<Integer> answers = Collections.nCopies(PersonalityProfile.QUESTION_COUNT, 5);
    double trait = (11.0 / 3 - 1) / 4;
    double stability = 1 - trait;

    // Act
    PersonalityProfile profile = PersonalityProfile.fromAnswers(answers);

    // Assert
    assertEquals(0.85 * trait + 0.15 * stability, profile.getLeadership(), DELTA);
    assertEquals(0.75 * trait + 0.25 * stability, profile.getProblemSolving(), DELTA);
    assertEquals(trait, profile.getCreativity(), DELTA);
    assertEquals(0.80 * trait + 0.20 * stability, profile.getExecution(), DELTA);
  }

  @Test
  @DisplayName("The most extreme answers still give scores between 0 and 1")
  void extremeAnswersStayInRange() {
    // Arrange - top on every direct item, bottom on every reversed one (and the opposite for neuroticism)
    List<Integer> highest = List.of(5, 5, 5, 1, 5, 1, 1, 1, 5, 1, 5, 5, 5, 1, 5);

    // Act
    PersonalityProfile profile = PersonalityProfile.fromAnswers(highest);

    // Assert
    assertEquals(1.0, profile.getLeadership(), DELTA);
    assertEquals(1.0, profile.getProblemSolving(), DELTA);
    assertEquals(1.0, profile.getCreativity(), DELTA);
    assertEquals(1.0, profile.getExecution(), DELTA);
  }

  @Test
  @DisplayName("The lowest possible answers give 0 on every score")
  void lowestAnswersGiveZero() {
    // Arrange - the mirror image of the highest answers
    List<Integer> lowest = List.of(1, 1, 1, 5, 1, 5, 5, 5, 1, 5, 1, 1, 1, 5, 1);

    // Act
    PersonalityProfile profile = PersonalityProfile.fromAnswers(lowest);

    // Assert
    assertEquals(0.0, profile.getLeadership(), DELTA);
    assertEquals(0.0, profile.getProblemSolving(), DELTA);
    assertEquals(0.0, profile.getCreativity(), DELTA);
    assertEquals(0.0, profile.getExecution(), DELTA);
  }

  @Test
  @DisplayName("The wrong number of answers is a VALIDATION_FAILED, not a crash")
  void wrongNumberOfAnswersIsRejected() {
    // Arrange
    List<Integer> answers = Collections.nCopies(PersonalityProfile.QUESTION_COUNT - 1, 3);

    // Act
    StudyMatchException e = assertThrows(StudyMatchException.class, () -> PersonalityProfile.fromAnswers(answers));

    // Assert
    assertEquals(ErrorCode.VALIDATION_FAILED, e.getCode());
    assertTrue(e.getParams().isEmpty());
  }

}
