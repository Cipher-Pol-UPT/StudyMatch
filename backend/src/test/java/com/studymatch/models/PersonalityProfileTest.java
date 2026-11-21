package com.studymatch.models;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.studymatch.exceptions.ErrorCode;
import com.studymatch.exceptions.StudyMatchException;

class PersonalityProfileTest {

  private static final double DELTA = 1e-4;

  @Test
  @DisplayName("The constructor keeps each trait in its own field")
  void constructorKeepsTraitOrder() {
    // Act
    PersonalityProfile profile = new PersonalityProfile(0.1, 0.2, 0.3, 0.4, 0.5);

    // Assert
    assertNull(profile.getId());
    assertEquals(0.1, profile.getOpenness(), DELTA);
    assertEquals(0.2, profile.getConscientiousness(), DELTA);
    assertEquals(0.3, profile.getExtraversion(), DELTA);
    assertEquals(0.4, profile.getAgreeableness(), DELTA);
    assertEquals(0.5, profile.getNeuroticism(), DELTA);
  }

  @Test
  @DisplayName("The empty constructor JPA uses starts every trait at 0")
  void emptyConstructorStartsAtZero() {
    // Act
    PersonalityProfile profile = new PersonalityProfile();

    // Assert
    assertEquals(0.0, profile.getOpenness(), DELTA);
    assertEquals(0.0, profile.getNeuroticism(), DELTA);
  }

  @Test
  @DisplayName("The worked example from the spec gives the expected traits")
  void scoresTheSpecExample() {
    // Arrange - openness is items 5, 10, 15, 20 (the last three reversed): 5, 6-1, 6-2, 6-1 = 19 / 4
    List<Integer> answers = List.of(4, 5, 2, 4, 5, 2, 2, 4, 3, 1, 4, 4, 3, 4, 2, 1, 1, 3, 2, 1);

    // Act
    PersonalityProfile profile = PersonalityProfile.fromAnswers(answers);

    // Assert
    assertEquals(0.9375, profile.getOpenness(), DELTA);
    assertEquals(0.375, profile.getConscientiousness(), DELTA);
    assertEquals(0.8125, profile.getExtraversion(), DELTA);
    assertEquals(0.875, profile.getAgreeableness(), DELTA);
    assertEquals(0.6875, profile.getNeuroticism(), DELTA);
  }

  @Test
  @DisplayName("All neutral answers give 0.5 on every trait")
  void neutralAnswersGiveMiddleScores() {
    // Arrange
    List<Integer> answers = Collections.nCopies(PersonalityProfile.QUESTION_COUNT, 3);

    // Act
    PersonalityProfile profile = PersonalityProfile.fromAnswers(answers);

    // Assert
    assertEquals(0.5, profile.getOpenness(), DELTA);
    assertEquals(0.5, profile.getConscientiousness(), DELTA);
    assertEquals(0.5, profile.getExtraversion(), DELTA);
    assertEquals(0.5, profile.getAgreeableness(), DELTA);
    assertEquals(0.5, profile.getNeuroticism(), DELTA);
  }

  @Test
  @DisplayName("Reversed questions are flipped before averaging")
  void reversedItemsAreFlipped() {
    // Arrange - two direct and two reversed items give (5 + 5 + 1 + 1) / 4, openness has three reversed: (5 + 1 + 1 + 1) / 4
    List<Integer> answers = Collections.nCopies(PersonalityProfile.QUESTION_COUNT, 5);

    // Act
    PersonalityProfile profile = PersonalityProfile.fromAnswers(answers);

    // Assert
    assertEquals(0.25, profile.getOpenness(), DELTA);
    assertEquals(0.5, profile.getConscientiousness(), DELTA);
    assertEquals(0.5, profile.getExtraversion(), DELTA);
    assertEquals(0.5, profile.getAgreeableness(), DELTA);
    assertEquals(0.5, profile.getNeuroticism(), DELTA);
  }

  @ParameterizedTest(name = "item {0} moves {1} by {2}")
  @CsvSource({
      "1, EXTRAVERSION, 0.0625", "2, AGREEABLENESS, 0.0625", "3, CONSCIENTIOUSNESS, 0.0625",
      "4, NEUROTICISM, 0.0625", "5, OPENNESS, 0.0625", "6, EXTRAVERSION, -0.0625", "7, AGREEABLENESS, -0.0625",
      "8, CONSCIENTIOUSNESS, -0.0625", "9, NEUROTICISM, -0.0625", "10, OPENNESS, -0.0625",
      "11, EXTRAVERSION, 0.0625", "12, AGREEABLENESS, 0.0625", "13, CONSCIENTIOUSNESS, 0.0625",
      "14, NEUROTICISM, 0.0625", "15, OPENNESS, -0.0625", "16, EXTRAVERSION, -0.0625",
      "17, AGREEABLENESS, -0.0625", "18, CONSCIENTIOUSNESS, -0.0625", "19, NEUROTICISM, -0.0625",
      "20, OPENNESS, -0.0625" })
  @DisplayName("Each Mini-IPIP item moves only its own trait, by a quarter of a point, the way its key says")
  void eachItemFollowsTheKey(int item, String trait, double change) {
    // Arrange - all neutral, then one item a point higher
    List<Integer> answers = new ArrayList<>(Collections.nCopies(PersonalityProfile.QUESTION_COUNT, 3));
    answers.set(item - 1, 4);

    // Act
    PersonalityProfile profile = PersonalityProfile.fromAnswers(answers);

    // Assert
    for (String other : PersonalityProfile.TRAITS) {
      double expected = other.equals(trait) ? 0.5 + change : 0.5;
      assertEquals(expected, score(profile, other), DELTA, other);
    }
  }

  @Test
  @DisplayName("The most extreme answers reach 0 and 1 without going past them")
  void extremeAnswersStayInRange() {
    // Arrange - top on every direct item, bottom on every reversed one (and the opposite for neuroticism)
    List<Integer> highest = List.of(5, 5, 5, 1, 5, 1, 1, 1, 5, 1, 5, 5, 5, 1, 1, 1, 1, 1, 5, 1);

    // Act
    PersonalityProfile profile = PersonalityProfile.fromAnswers(highest);

    // Assert
    assertEquals(1.0, profile.getOpenness(), DELTA);
    assertEquals(1.0, profile.getConscientiousness(), DELTA);
    assertEquals(1.0, profile.getExtraversion(), DELTA);
    assertEquals(1.0, profile.getAgreeableness(), DELTA);
    assertEquals(0.0, profile.getNeuroticism(), DELTA);
  }

  @Test
  @DisplayName("The opposite extreme answers flip every trait")
  void lowestAnswersGiveZero() {
    // Arrange - the mirror image of the highest answers
    List<Integer> lowest = List.of(1, 1, 1, 5, 1, 5, 5, 5, 1, 5, 1, 1, 1, 5, 5, 5, 5, 5, 1, 5);

    // Act
    PersonalityProfile profile = PersonalityProfile.fromAnswers(lowest);

    // Assert
    assertEquals(0.0, profile.getOpenness(), DELTA);
    assertEquals(0.0, profile.getConscientiousness(), DELTA);
    assertEquals(0.0, profile.getExtraversion(), DELTA);
    assertEquals(0.0, profile.getAgreeableness(), DELTA);
    assertEquals(1.0, profile.getNeuroticism(), DELTA);
  }

  @Test
  @DisplayName("The strongest trait is the highest score")
  void strongestTraitIsTheHighest() {
    // Arrange
    PersonalityProfile profile = new PersonalityProfile(0.2, 0.4, 0.9, 0.3, 0.1);

    // Act + Assert
    assertEquals("EXTRAVERSION", profile.strongestTrait());
  }

  @Test
  @DisplayName("A tie goes to the trait that comes first in OCEAN order")
  void strongestTraitTieGoesToTheFirst() {
    // Arrange
    PersonalityProfile profile = new PersonalityProfile(0.5, 0.7, 0.2, 0.7, 0.7);

    // Act + Assert
    assertEquals("CONSCIENTIOUSNESS", profile.strongestTrait());
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

  private static double score(PersonalityProfile profile, String trait) {
    return switch (trait) {
      case "OPENNESS" -> profile.getOpenness();
      case "CONSCIENTIOUSNESS" -> profile.getConscientiousness();
      case "EXTRAVERSION" -> profile.getExtraversion();
      case "AGREEABLENESS" -> profile.getAgreeableness();
      default -> profile.getNeuroticism();
    };
  }

}
