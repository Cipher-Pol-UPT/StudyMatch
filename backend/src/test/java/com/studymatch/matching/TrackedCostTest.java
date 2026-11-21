package com.studymatch.matching;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Random;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class TrackedCostTest {

  private static final double DELTA = 1e-9;

  @ParameterizedTest(name = "{0} students, seed {1}")
  @CsvSource({ "20, 1", "30, 2", "65, 3" })
  @DisplayName("After every swap and move the tracked cost equals the cost worked out from scratch")
  void matchesFullCost(int students, long seed) {
    // Arrange
    MatchingProblem problem = MatchingTestData.randomProblem(students, 0.05, seed, 3, 4, 5);
    Grouping start = new GreedyMatching().solve(problem, MatchingBudget.evaluations(0), new Random(seed));
    TrackedCost tracked = new TrackedCost(problem, start);
    Random random = new Random(seed);
    assertEquals(MatchingCost.cost(problem, start), tracked.total(), DELTA);

    for (int step = 0; step < 300; step++) {
      // Act
      int student = random.nextInt(problem.size());
      if (random.nextBoolean()) {
        tracked.swap(student, random.nextInt(problem.size()));
      } else {
        tracked.move(student, random.nextInt(problem.getGroupCount()));
      }

      // Assert
      assertEquals(MatchingCost.cost(problem, tracked.grouping()), tracked.total(), DELTA);
    }
  }

  @Test
  @DisplayName("Swapping the same two students again brings the cost back")
  void swapTwiceUndoes() {
    // Arrange
    MatchingProblem problem = MatchingTestData.randomProblem(30, 0, 4, 3, 4, 5);
    TrackedCost tracked = new TrackedCost(problem,
        new GreedyMatching().solve(problem, MatchingBudget.evaluations(0), new Random(4)));
    double before = tracked.total();
    int other = tracked.groupOf(0) == tracked.groupOf(1) ? 29 : 1;

    // Act
    tracked.swap(0, other);
    tracked.swap(0, other);

    // Assert
    assertEquals(before, tracked.total(), DELTA);
  }

}
