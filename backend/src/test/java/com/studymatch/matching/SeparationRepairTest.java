package com.studymatch.matching;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SeparationRepairTest {

  @Test
  @DisplayName("A separated pair in the same group is swapped apart")
  void repairSwapsPairApart() {
    // Arrange - groups of 2, students 0 and 1 are separated but together
    MatchingProblem problem = MatchingTestData.fixedSizeProblem(4, 2, List.of(new int[] { 0, 1 }));
    Grouping grouping = new Grouping(new int[] { 0, 0, 1, 1 }, 2);

    // Act
    SeparationRepair.repair(problem, grouping, new Random(1));

    // Assert
    assertEquals(0, SeparationRepair.broken(problem, grouping));
    assertTrue(MatchingCost.isValid(problem, grouping));
  }

  @Test
  @DisplayName("When every swap would break another separation the groups are left as they were")
  void repairGivesUpWithoutBreakingAnother() {
    // Arrange - 0 and 1 are together, and every swap puts one of them next to someone they're separated from
    MatchingProblem problem = MatchingTestData.fixedSizeProblem(6, 2, List.of(new int[] { 0, 1 },
        new int[] { 0, 3 }, new int[] { 0, 5 }, new int[] { 1, 3 }, new int[] { 1, 5 }));
    int[] before = { 0, 0, 1, 1, 2, 2 };
    Grouping grouping = new Grouping(before.clone(), 3);

    // Act
    SeparationRepair.repair(problem, grouping, new Random(1));

    // Assert
    assertEquals(1, SeparationRepair.broken(problem, grouping));
    assertArrayEquals(before, IntStream.range(0, 6).map(grouping::groupOf).toArray());
  }

  @Test
  @DisplayName("Every broken separation adds the penalty to the cost")
  void penaltyPerBrokenSeparation() {
    // Arrange - two separated pairs, both together
    MatchingProblem problem = MatchingTestData.fixedSizeProblem(4, 2,
        List.of(new int[] { 0, 1 }, new int[] { 2, 3 }));
    Grouping grouping = new Grouping(new int[] { 0, 0, 1, 1 }, 2);

    // Act
    double penalized = SeparationRepair.penalizedCost(problem, grouping);

    // Assert
    assertEquals(MatchingCost.cost(problem, grouping) + 2 * SeparationRepair.PENALTY, penalized, 1e-9);
  }

}
