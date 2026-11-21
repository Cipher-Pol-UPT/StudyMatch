package com.studymatch.matching;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

// the same checks for every algorithm added from the table of metaheuristics
class MetaheuristicsTest {

  private static final long BUDGET = 20_000;

  static Stream<Arguments> algorithms() {
    return Stream.of(
        Arguments.of("late acceptance", (Supplier<MatchingAlgorithm>) LateAcceptanceMatching::new),
        Arguments.of("bee colony", (Supplier<MatchingAlgorithm>) BeeColonyMatching::new),
        Arguments.of("cuckoo search", (Supplier<MatchingAlgorithm>) CuckooSearchMatching::new),
        Arguments.of("clonal selection", (Supplier<MatchingAlgorithm>) ClonalSelectionMatching::new),
        Arguments.of("chemical reaction", (Supplier<MatchingAlgorithm>) ChemicalReactionMatching::new),
        Arguments.of("particle swarm", (Supplier<MatchingAlgorithm>) ParticleSwarmMatching::new),
        Arguments.of("black hole", (Supplier<MatchingAlgorithm>) BlackHoleMatching::new),
        Arguments.of("bees algorithm", (Supplier<MatchingAlgorithm>) BeesAlgorithmMatching::new),
        Arguments.of("shuffled frog leaping", (Supplier<MatchingAlgorithm>) ShuffledFrogLeapingMatching::new),
        Arguments.of("invasive weed", (Supplier<MatchingAlgorithm>) InvasiveWeedMatching::new),
        Arguments.of("grey wolf", (Supplier<MatchingAlgorithm>) GreyWolfMatching::new),
        Arguments.of("great deluge", (Supplier<MatchingAlgorithm>) GreatDelugeMatching::new),
        Arguments.of("firefly", (Supplier<MatchingAlgorithm>) FireflyMatching::new),
        Arguments.of("whale", (Supplier<MatchingAlgorithm>) WhaleMatching::new),
        Arguments.of("bacterial foraging", (Supplier<MatchingAlgorithm>) BacterialForagingMatching::new),
        Arguments.of("imperialist competitive", (Supplier<MatchingAlgorithm>) ImperialistCompetitiveMatching::new),
        Arguments.of("chicken swarm", (Supplier<MatchingAlgorithm>) ChickenSwarmMatching::new),
        Arguments.of("spider monkey", (Supplier<MatchingAlgorithm>) SpiderMonkeyMatching::new),
        Arguments.of("iterated local search", (Supplier<MatchingAlgorithm>) IteratedLocalSearchMatching::new),
        Arguments.of("variable neighbourhood", (Supplier<MatchingAlgorithm>) VariableNeighbourhoodMatching::new));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("algorithms")
  @DisplayName("Every student is placed once, within the sizes and away from who they're separated from")
  void resultIsValid(String name, Supplier<MatchingAlgorithm> algorithm) {
    // Arrange - 10% separated
    MatchingProblem problem = MatchingTestData.randomProblem(40, 0.1, 3, 3, 4, 5);

    // Act
    Grouping grouping = algorithm.get().solve(problem, MatchingBudget.evaluations(BUDGET), new Random(3));

    // Assert
    assertTrue(MatchingCost.isValid(problem, grouping));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("algorithms")
  @DisplayName("The same seed gives the same groups")
  void sameSeedSameGroups(String name, Supplier<MatchingAlgorithm> algorithm) {
    // Arrange
    MatchingProblem problem = MatchingTestData.randomProblem(30, 0.05, 7, 3, 4, 5);

    // Act
    Grouping first = algorithm.get().solve(problem, MatchingBudget.evaluations(BUDGET), new Random(42));
    Grouping second = algorithm.get().solve(problem, MatchingBudget.evaluations(BUDGET), new Random(42));

    // Assert
    for (int student = 0; student < problem.size(); student++) {
      assertEquals(first.groupOf(student), second.groupOf(student));
    }
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("algorithms")
  @DisplayName("A pool that is one group finishes")
  void singleGroupFinishes(String name, Supplier<MatchingAlgorithm> algorithm) {
    // Arrange - 4 students, groups of exactly 4
    MatchingProblem problem = MatchingTestData.randomProblem(4, 0, 1, 4, 4, 4);

    // Act
    Grouping grouping = algorithm.get().solve(problem, MatchingBudget.evaluations(BUDGET), new Random(1));

    // Assert
    assertEquals(1, grouping.groupCount());
  }

}
