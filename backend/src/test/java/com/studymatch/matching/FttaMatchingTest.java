package com.studymatch.matching;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class FttaMatchingTest {

  private static final long BUDGET = 2_000;

  private final FttaMatching ftta = new FttaMatching();

  @ParameterizedTest(name = "{0} students, seed {1}")
  @CsvSource({ "20, 1", "30, 2", "61, 3" })
  @DisplayName("Every student is placed once, within the sizes and away from who they're separated from")
  void resultIsValid(int students, long seed) {
    // Arrange - 5% of the pairs separated
    MatchingProblem problem = MatchingTestData.randomProblem(students, 0.05, seed, 3, 4, 5);

    // Act
    Grouping grouping = ftta.solve(problem, MatchingBudget.evaluations(BUDGET), new Random(seed));

    // Assert
    assertTrue(MatchingCost.isValid(problem, grouping));
  }

  @Test
  @DisplayName("The same seed gives the same groups")
  void sameSeedSameGroups() {
    // Arrange
    MatchingProblem problem = MatchingTestData.randomProblem(30, 0.05, 7, 3, 4, 5);

    // Act
    Grouping first = ftta.solve(problem, MatchingBudget.evaluations(BUDGET), new Random(42));
    Grouping second = ftta.solve(problem, MatchingBudget.evaluations(BUDGET), new Random(42));

    // Assert
    for (int student = 0; student < problem.size(); student++) {
      assertEquals(first.groupOf(student), second.groupOf(student));
    }
  }

}
