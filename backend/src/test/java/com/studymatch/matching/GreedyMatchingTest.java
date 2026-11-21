package com.studymatch.matching;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class GreedyMatchingTest {

  private static final long BUDGET = 10_000;

  private final GreedyMatching greedy = new GreedyMatching();

  @ParameterizedTest(name = "{0} students, seed {1}")
  @CsvSource({ "20, 1", "20, 2", "30, 3", "61, 4", "61, 5" })
  @DisplayName("Every student is placed once, within the sizes and away from who they're separated from")
  void resultIsValid(int students, long seed) {
    // Arrange - 5% of the pairs separated
    MatchingProblem problem = MatchingTestData.randomProblem(students, 0.05, seed, 3, 4, 5);

    // Act
    Grouping grouping = greedy.solve(problem, MatchingBudget.evaluations(BUDGET), new Random(seed));

    // Assert
    assertEquals(problem.getGroupCount(), grouping.groupCount());
    assertTrue(MatchingCost.isValid(problem, grouping));
  }

  @Test
  @DisplayName("The same seed gives the same groups")
  void sameSeedSameGroups() {
    // Arrange
    MatchingProblem problem = MatchingTestData.randomProblem(30, 0.05, 7, 3, 4, 5);

    // Act
    Grouping first = greedy.solve(problem, MatchingBudget.evaluations(BUDGET), new Random(42));
    Grouping second = greedy.solve(problem, MatchingBudget.evaluations(BUDGET), new Random(42));

    // Assert
    for (int student = 0; student < problem.size(); student++) {
      assertEquals(first.groupOf(student), second.groupOf(student));
    }
  }

  @Test
  @DisplayName("Separations that leave no valid grouping are reported")
  void impossibleSeparations() {
    // Arrange - groups of 2, and student 0 is separated from everyone else
    MatchingProblem base = MatchingTestData.randomProblem(4, 0, 1, 2, 2, 2);
    List<MatchingProblem.Member> members = List.of(base.member(0), base.member(1), base.member(2), base.member(3));
    MatchingProblem problem = new MatchingProblem(members,
        List.of(new int[] { 0, 1 }, new int[] { 0, 2 }, new int[] { 0, 3 }), 2, 2, 2);

    // Act + Assert
    assertThrows(IllegalStateException.class, () -> greedy.solve(problem, MatchingBudget.evaluations(BUDGET), new Random(1)));
  }

}
