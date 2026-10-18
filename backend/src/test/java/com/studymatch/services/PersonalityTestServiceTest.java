package com.studymatch.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.studymatch.models.PersonalityProfile;

class PersonalityTestServiceTest {

  private static final double DELTA = 1e-4;

  private final PersonalityTestService personalityTestService = new PersonalityTestService();

  @Test
  void scoresTheSpecExample() {
    PersonalityProfile profile = personalityTestService.score(List.of(4, 5, 2, 4, 5, 2, 2, 4, 3, 1, 4, 4, 3, 4, 5));

    assertEquals(0.6417, profile.getLeadership(), DELTA);
    assertEquals(0.6333, profile.getProblemSolving(), DELTA);
    assertEquals(0.9375, profile.getCreativity(), DELTA);
    assertEquals(0.4333, profile.getExecution(), DELTA);
  }

  @Test
  void neutralAnswersGiveMiddleScores() {
    PersonalityProfile profile = personalityTestService.score(Collections.nCopies(15, 3));

    assertEquals(0.5, profile.getLeadership(), DELTA);
    assertEquals(0.5, profile.getProblemSolving(), DELTA);
    assertEquals(0.5, profile.getCreativity(), DELTA);
    assertEquals(0.5, profile.getExecution(), DELTA);
  }

  @Test
  void reversedItemsCancelOutWhenEveryAnswerIsTheSame() {
    // Each trait has two direct items and one reversed item, so all 5s give a mean of (5 + 5 + 1) / 3.
    double trait = (11.0 / 3 - 1) / 4;
    double stability = 1 - trait;
    PersonalityProfile profile = personalityTestService.score(Collections.nCopies(15, 5));

    assertEquals(0.85 * trait + 0.15 * stability, profile.getLeadership(), DELTA);
    assertEquals(0.75 * trait + 0.25 * stability, profile.getProblemSolving(), DELTA);
    assertEquals(trait, profile.getCreativity(), DELTA);
    assertEquals(0.80 * trait + 0.20 * stability, profile.getExecution(), DELTA);
  }

  @Test
  void rejectsTheWrongNumberOfAnswers() {
    assertThrows(IllegalArgumentException.class, () -> personalityTestService.score(Collections.nCopies(14, 3)));
  }

}
