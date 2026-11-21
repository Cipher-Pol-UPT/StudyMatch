package com.studymatch.matching;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class BeeColonyMatchingTest {

  private static final long BUDGET = 20_000;

  private final BeeColonyMatching colony = new BeeColonyMatching();

  @ParameterizedTest(name = "{0} students, seed {1}")
  @CsvSource({ "20, 1", "40, 2", "81, 3" })
  @DisplayName("Every student is placed once, within the sizes and away from who they're separated from")
  void resultIsValid(int students, long seed) {
    // Arrange - 10% separated
    MatchingProblem problem = MatchingTestData.randomProblem(students, 0.1, seed, 3, 4, 5);

    // Act
    Grouping grouping = colony.solve(problem, MatchingBudget.evaluations(BUDGET), new Random(seed));

    // Assert
    assertTrue(MatchingCost.isValid(problem, grouping));
  }

  @Test
  @DisplayName("The same seed gives the same groups")
  void sameSeedSameGroups() {
    // Arrange
    MatchingProblem problem = MatchingTestData.randomProblem(30, 0.05, 7, 3, 4, 5);

    // Act
    Grouping first = colony.solve(problem, MatchingBudget.evaluations(BUDGET), new Random(42));
    Grouping second = colony.solve(problem, MatchingBudget.evaluations(BUDGET), new Random(42));

    // Assert
    for (int student = 0; student < problem.size(); student++) {
      assertEquals(first.groupOf(student), second.groupOf(student));
    }
  }

  @Test
  @DisplayName("Without a temperature (the pure bee colony) the groups are valid too")
  void pureColonyIsValid() {
    // Arrange
    MatchingProblem problem = MatchingTestData.randomProblem(40, 0.1, 4, 3, 4, 5);
    BeeColonyMatching pure = new BeeColonyMatching(BeeColonyMatching.DEFAULTS.withTemperature(0));

    // Act
    Grouping grouping = pure.solve(problem, MatchingBudget.evaluations(BUDGET), new Random(4));

    // Assert
    assertTrue(MatchingCost.isValid(problem, grouping));
  }

  @Test
  @DisplayName("A pool that is one group finishes")
  void singleGroupFinishes() {
    // Arrange - 4 students, groups of exactly 4
    MatchingProblem problem = MatchingTestData.randomProblem(4, 0, 1, 4, 4, 4);

    // Act
    Grouping grouping = colony.solve(problem, MatchingBudget.evaluations(BUDGET), new Random(1));

    // Assert
    assertEquals(1, grouping.groupCount());
  }

}
