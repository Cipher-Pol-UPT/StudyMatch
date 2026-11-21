package com.studymatch.matching;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class AntColonyMatchingTest {

  private static final long BUDGET = 200;

  private final AntColonyMatching colony = new AntColonyMatching();

  @ParameterizedTest(name = "{0} students, seed {1}")
  @CsvSource({ "20, 1", "30, 2", "61, 3" })
  @DisplayName("Every student is placed once, within the sizes and away from who they're separated from")
  void resultIsValid(int students, long seed) {
    // Arrange - 5% of the pairs separated
    MatchingProblem problem = MatchingTestData.randomProblem(students, 0.05, seed, 3, 4, 5);

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
  @DisplayName("Separations that leave no valid grouping are reported")
  void impossibleSeparations() {
    // Arrange - groups of 2, and student 0 is separated from everyone else
    MatchingProblem base = MatchingTestData.randomProblem(4, 0, 1, 2, 2, 2);
    List<MatchingProblem.Member> members = List.of(base.member(0), base.member(1), base.member(2), base.member(3));
    MatchingProblem problem = new MatchingProblem(members,
        List.of(new int[] { 0, 1 }, new int[] { 0, 2 }, new int[] { 0, 3 }), 2, 2, 2);

    // Act + Assert
    assertThrows(IllegalStateException.class,
        () -> colony.solve(problem, MatchingBudget.evaluations(BUDGET), new Random(1)));
  }

  @Test
  @DisplayName("Parallel ants give valid groups, the same ones every time for the same seed")
  void parallelIsValidAndRepeatable() {
    // Arrange - every ant polishes, so a bigger budget than the others
    MatchingProblem problem = MatchingTestData.randomProblem(40, 0.05, 5, 3, 4, 5);
    AntColonyMatching parallel = new AntColonyMatching(AntColonyMatching.DEFAULTS, true);

    // Act
    Grouping first = parallel.solve(problem, MatchingBudget.evaluations(500_000), new Random(9));
    Grouping second = parallel.solve(problem, MatchingBudget.evaluations(500_000), new Random(9));

    // Assert
    assertTrue(MatchingCost.isValid(problem, first));
    for (int student = 0; student < problem.size(); student++) {
      assertEquals(first.groupOf(student), second.groupOf(student));
    }
  }

  @Test
  @DisplayName("Parallel ants refuse a processor time budget, the other cores would work for free")
  void parallelRefusesProcessorTime() {
    // Arrange
    MatchingProblem problem = MatchingTestData.randomProblem(20, 0, 1, 3, 4, 5);
    AntColonyMatching parallel = new AntColonyMatching(AntColonyMatching.DEFAULTS, true);

    // Act + Assert
    assertThrows(IllegalStateException.class,
        () -> parallel.solve(problem, MatchingBudget.cpuTime(Duration.ofSeconds(1)), new Random(1)));
  }

  @ParameterizedTest(name = "tabu {0}, rotations {1}, local fading {2}")
  @CsvSource({ "10, 0, 0", "0, 0.5, 0", "0, 0, 0.1", "10, 0.5, 0.1" })
  @DisplayName("Tabu search, rotations and local pheromone fading all give valid groups")
  void localSearchVariantsAreValid(int tenure, double rotations, double localFade) {
    // Arrange - 10% separated, so the separation checks of each variant get used
    MatchingProblem problem = MatchingTestData.randomProblem(40, 0.1, 6, 3, 4, 5);
    AntColonyMatching.Settings defaults = AntColonyMatching.DEFAULTS;
    AntColonyMatching variant = new AntColonyMatching(defaults.withLocalEvaporation(localFade).withLocalSearch(
        defaults.localSearch().withTabu(tenure, 20).withRotationShare(rotations).withSteps(2_000)));

    // Act
    Grouping grouping = variant.solve(problem, MatchingBudget.evaluations(20_000), new Random(6));

    // Assert
    assertTrue(MatchingCost.isValid(problem, grouping));
  }

}
