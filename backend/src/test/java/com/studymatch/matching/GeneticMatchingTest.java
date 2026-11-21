package com.studymatch.matching;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class GeneticMatchingTest {

  private static final long BUDGET = 2_000;

  private final GeneticMatching genetic = new GeneticMatching();

  @ParameterizedTest(name = "{0} students, seed {1}")
  @CsvSource({ "20, 1", "61, 2", "20, 3", "61, 4" })
  @DisplayName("Every student is placed once, within the sizes and away from who they're separated from")
  void resultIsValid(int students, long seed) {
    // Arrange - 5% of the pairs separated
    MatchingProblem problem = MatchingTestData.randomProblem(students, 0.05, seed, 3, 4, 5);

    // Act
    Grouping grouping = genetic.solve(problem, MatchingBudget.evaluations(BUDGET), new Random(seed));

    // Assert
    assertTrue(MatchingCost.isValid(problem, grouping));
  }

  @Test
  @DisplayName("The same seed gives the same groups")
  void sameSeedSameGroups() {
    // Arrange
    MatchingProblem problem = MatchingTestData.randomProblem(30, 0.05, 7, 3, 4, 5);

    // Act
    Grouping first = genetic.solve(problem, MatchingBudget.evaluations(BUDGET), new Random(42));
    Grouping second = genetic.solve(problem, MatchingBudget.evaluations(BUDGET), new Random(42));

    // Assert
    for (int student = 0; student < problem.size(); student++) {
      assertEquals(first.groupOf(student), second.groupOf(student));
    }
  }

  @Test
  @DisplayName("Order crossover always gives every student exactly once")
  void crossoverKeepsEveryStudentOnce() {
    // Arrange
    Random random = new Random(1);
    int[] everyone = IntStream.range(0, 12).toArray();

    for (int i = 0; i < 500; i++) {
      int[] first = shuffled(random);
      int[] second = shuffled(random);

      // Act
      int[] child = GeneticMatching.orderCrossover(first, second, random);

      // Assert
      int[] sorted = child.clone();
      Arrays.sort(sorted);
      assertArrayEquals(everyone, sorted);
    }
  }

  private static int[] shuffled(Random random) {
    List<Integer> order = new ArrayList<>(IntStream.range(0, 12).boxed().toList());
    Collections.shuffle(order, random);
    return order.stream().mapToInt(Integer::intValue).toArray();
  }

}
