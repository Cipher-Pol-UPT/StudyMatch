package com.studymatch.matching;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;

// not a test: tries settings around the current defaults. Run one with
// ./mvnw test -Djacoco.skip=true -Dtest='MatchingTuning#tuneBeeColony'. How the defaults were found is in
// docs/group-matching.md
class MatchingTuning {

  // other classes than the benchmark's (seeds 201 and up), so the tuning doesn't fit the classes we score on
  private static final long[] SEEDS = { 101, 102, 103 };
  private static final int STUDENTS = 80;
  private static final Duration BUDGET = Duration.ofSeconds(5);

  private record Result(String settings, double cost) {
  }

  @Test
  void tuneAnnealing() {
    Map<String, Supplier<MatchingAlgorithm>> candidates = new LinkedHashMap<>();
    for (double start : new double[] { 0.3, 1, 3 }) {
      for (double end : new double[] { 0.01, 0.001, 0.0001 }) {
        SimulatedAnnealingMatching.Settings settings = new SimulatedAnnealingMatching.Settings(start, end);
        candidates.put(settings.toString(), () -> new SimulatedAnnealingMatching(settings));
      }
    }
    print("annealing", tune(candidates));
  }

  // the local search's temperature, the fairness while building and the local search length
  @Test
  void tuneAnts() {
    AntColonyMatching.Settings base = AntColonyMatching.DEFAULTS;
    Map<String, Supplier<MatchingAlgorithm>> candidates = new LinkedHashMap<>();
    candidates.put("annealing (reference)", SimulatedAnnealingMatching::new);
    for (double temperature : new double[] { 0, 0.2, 0.5 }) {
      for (double fairness : new double[] { 2, 4 }) {
        for (int steps : new int[] { 20_000, 40_000 }) {
          AntColonyMatching.Settings settings = new AntColonyMatching.Settings(base.ants(), base.pheromonePower(),
              base.heuristicPower(), base.evaporation(), base.minPheromone(), base.globalBestEvery(),
              base.stagnationRounds(), fairness, base.localSearch().withTemperature(temperature).withSteps(steps));
          candidates.put(settings.toString(), () -> new AntColonyMatching(settings));
        }
      }
    }
    print("ants", tune(candidates));
  }

  @Test
  void tuneBeeColony() {
    Map<String, Supplier<MatchingAlgorithm>> candidates = new LinkedHashMap<>();
    candidates.put("annealing (reference)", SimulatedAnnealingMatching::new);
    for (int sources : new int[] { 10, 20 }) {
      for (double partner : new double[] { 0.75, 0.9 }) {
        for (double temperature : new double[] { 0, 0.05, 0.2, 0.5 }) {
          BeeColonyMatching.Settings settings = new BeeColonyMatching.Settings(sources, 3_000, partner, temperature);
          candidates.put(settings.toString(), () -> new BeeColonyMatching(settings));
        }
      }
    }
    print("bee colony", tune(candidates));
  }

  private static List<Result> tune(Map<String, Supplier<MatchingAlgorithm>> candidates) {
    List<Result> results = new ArrayList<>();
    for (Map.Entry<String, Supplier<MatchingAlgorithm>> candidate : candidates.entrySet()) {
      results.add(new Result(candidate.getKey(), averageCost(candidate.getValue())));
    }
    return results;
  }

  private static double averageCost(Supplier<MatchingAlgorithm> algorithm) {
    double sum = 0;
    for (long seed : SEEDS) {
      MatchingProblem problem = MatchingTestData.randomProblem(STUDENTS, 0.05, seed, 3, 4, 5);
      Grouping grouping = algorithm.get().solve(problem, MatchingBudget.cpuTime(BUDGET), new Random(seed));
      sum += MatchingCost.cost(problem, grouping);
    }
    return sum / SEEDS.length;
  }

  private static void print(String name, List<Result> results) {
    System.out.printf(Locale.ROOT, "%n%s, best first:%n", name);
    results.stream().sorted((first, second) -> Double.compare(first.cost(), second.cost()))
        .forEach(result -> System.out.printf(Locale.ROOT, "  %.3f  %s%n", result.cost(), result.settings()));
  }

}
