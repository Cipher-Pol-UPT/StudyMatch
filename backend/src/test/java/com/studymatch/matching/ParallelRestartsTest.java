package com.studymatch.matching;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ParallelRestartsTest {

  private static final long BUDGET = 4_000;

  private final ParallelRestarts restarts = new ParallelRestarts(SimulatedAnnealingMatching::new, 4);

  @Test
  @DisplayName("The best of the parallel copies is a valid grouping")
  void resultIsValid() {
    // Arrange
    MatchingProblem problem = MatchingTestData.randomProblem(40, 0.05, 1, 3, 4, 5);

    // Act
    Grouping grouping = restarts.solve(problem, MatchingBudget.evaluations(BUDGET), new Random(1));

    // Assert
    assertTrue(MatchingCost.isValid(problem, grouping));
  }

  @Test
  @DisplayName("The same seed gives the same groups, whichever copy finishes first")
  void sameSeedSameGroups() {
    // Arrange
    MatchingProblem problem = MatchingTestData.randomProblem(30, 0.05, 7, 3, 4, 5);

    // Act
    Grouping first = restarts.solve(problem, MatchingBudget.evaluations(BUDGET), new Random(42));
    Grouping second = restarts.solve(problem, MatchingBudget.evaluations(BUDGET), new Random(42));

    // Assert
    for (int student = 0; student < problem.size(); student++) {
      assertEquals(first.groupOf(student), second.groupOf(student));
    }
  }

  @Test
  @DisplayName("The work of every copy counts against the budget")
  void copiesUseTheBudget() {
    // Arrange - 4 copies get 1000 calculations each
    MatchingProblem problem = MatchingTestData.randomProblem(30, 0, 3, 3, 4, 5);
    MatchingBudget budget = MatchingBudget.evaluations(BUDGET);

    // Act
    restarts.solve(problem, budget, new Random(3));

    // Assert
    assertEquals(BUDGET, budget.getUsed());
  }

}
