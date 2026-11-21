package com.studymatch.matching;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SimulatedAnnealingMatchingTest {

  private static final long BUDGET = 5_000;

  private final SimulatedAnnealingMatching annealing = new SimulatedAnnealingMatching();

  @ParameterizedTest(name = "{0} students, seed {1}")
  @CsvSource({ "20, 1", "30, 2", "61, 3" })
  @DisplayName("Every student is placed once, within the sizes and away from who they're separated from")
  void resultIsValid(int students, long seed) {
    // Arrange - 5% of the pairs separated
    MatchingProblem problem = MatchingTestData.randomProblem(students, 0.05, seed, 3, 4, 5);

    // Act
    Grouping grouping = annealing.solve(problem, MatchingBudget.evaluations(BUDGET), new Random(seed));

    // Assert
    assertTrue(MatchingCost.isValid(problem, grouping));
  }

  @Test
  @DisplayName("The same seed gives the same groups")
  void sameSeedSameGroups() {
    // Arrange
    MatchingProblem problem = MatchingTestData.randomProblem(30, 0.05, 7, 3, 4, 5);

    // Act
    Grouping first = annealing.solve(problem, MatchingBudget.evaluations(BUDGET), new Random(42));
    Grouping second = annealing.solve(problem, MatchingBudget.evaluations(BUDGET), new Random(42));

    // Assert
    for (int student = 0; student < problem.size(); student++) {
      assertEquals(first.groupOf(student), second.groupOf(student));
    }
  }

  @ParameterizedTest(name = "seed {0}")
  @CsvSource({ "1", "2", "3" })
  @DisplayName("The result is never worse than the greedy grouping it starts from")
  void neverWorseThanGreedy(long seed) {
    // Arrange
    MatchingProblem problem = MatchingTestData.randomProblem(30, 0.05, seed, 3, 4, 5);
    Grouping start = new GreedyMatching().solve(problem, MatchingBudget.evaluations(BUDGET), new Random(seed));

    // Act - the same seed, so annealing starts from that same greedy grouping
    Grouping result = annealing.solve(problem, MatchingBudget.evaluations(BUDGET), new Random(seed));

    // Assert
    assertTrue(MatchingCost.cost(problem, result) <= MatchingCost.cost(problem, start));
  }

}
