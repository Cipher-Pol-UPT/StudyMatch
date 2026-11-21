package com.studymatch.matching;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Test;

// not a test: compares the algorithms on generated classes. Run one comparison with
// ./mvnw test -Djacoco.skip=true -Dtest='MatchingBenchmark#compareQuick'. The results are in docs/group-matching.md
class MatchingBenchmark {

  private static final int SECONDS = 10;
  private static final int CORES = Runtime.getRuntime().availableProcessors();
  // classes none of the algorithms were tuned on (tuning uses seeds 101-103)
  private static final long FIRST_UNSEEN_SEED = 201;

  // the final comparison: every class size and separation level, 10 classes x 3 runs each (about 2.5 hours)
  @Test
  void compareFinal() throws IOException {
    compareAll(10, 3);
  }

  // the same on 5 classes x 2 runs (about 1 hour)
  @Test
  void compareQuick() throws IOException {
    compareAll(5, 2);
  }

  // on the clock, so it needs a quiet machine: annealing restarts on every core against ants on every core
  @Test
  void compareParallel() throws IOException {
    Map<String, MatchingAlgorithm> algorithms = new LinkedHashMap<>();
    algorithms.put("annealing x" + CORES, new ParallelRestarts(SimulatedAnnealingMatching::new, CORES));
    algorithms.put("parallel ants", new AntColonyMatching(AntColonyMatching.DEFAULTS.withAnts(CORES), true));
    arena(algorithms, 80, 0.05, 5, 2, true, Path.of("target", "matching-parallel.csv"));
  }

  private static void compareAll(int classes, int repeats) throws IOException {
    for (int students : new int[] { 40, 80, 120 }) {
      for (double separations : new double[] { 0.05, 0.15 }) {
        arena(algorithms(), students, separations, classes, repeats, false, Path.of("target",
            "matching-" + students + "-" + Math.round(separations * 100) + ".csv"));
      }
    }
  }

  // annealing first, the others are compared to it
  private static Map<String, MatchingAlgorithm> algorithms() {
    Map<String, MatchingAlgorithm> algorithms = new LinkedHashMap<>();
    algorithms.put("annealing", new SimulatedAnnealingMatching());
    algorithms.put("bee colony", new BeeColonyMatching());
    algorithms.put("ants", new AntColonyMatching());
    algorithms.put("genetic", new GeneticMatching());
    algorithms.put("ftta", new FttaMatching());
    algorithms.put("greedy", new GreedyMatching());
    return algorithms;
  }

  // every class several times; processor time, so other programs don't change the results, except for the parallel
  // ones, which need the clock since processor time only counts one thread
  private static void arena(Map<String, MatchingAlgorithm> algorithms, int students, double separations,
      int classes, int repeats, boolean wallClock, Path output) throws IOException {
    List<String> lines = new ArrayList<>(List.of("algorithm,class,run,cost"));
    Map<String, List<Double>> costs = new LinkedHashMap<>();
    for (long seed = FIRST_UNSEEN_SEED; seed < FIRST_UNSEEN_SEED + classes; seed++) {
      MatchingProblem problem = MatchingTestData.randomProblem(students, separations, seed, 3, 4, 5);
      for (int repeat = 1; repeat <= repeats; repeat++) {
        for (Map.Entry<String, MatchingAlgorithm> algorithm : algorithms.entrySet()) {
          Duration limit = Duration.ofSeconds(SECONDS);
          MatchingBudget budget = wallClock ? MatchingBudget.time(limit) : MatchingBudget.cpuTime(limit);
          Grouping grouping = algorithm.getValue().solve(problem, budget, new Random(seed * 100 + repeat));
          double cost = MatchingCost.cost(problem, grouping);
          costs.computeIfAbsent(algorithm.getKey(), name -> new ArrayList<>()).add(cost);
          lines.add(String.format(Locale.ROOT, "\"%s\",%d,%d,%.4f", algorithm.getKey(), seed, repeat, cost));
        }
      }
    }
    Files.write(output, lines);
    printSummary(costs, students, separations, classes, repeats);
  }

  // per algorithm: average, worst and best run, and against the first one, class by class
  private static void printSummary(Map<String, List<Double>> costs, int students, double separations, int classes,
      int repeats) {
    System.out.printf(Locale.ROOT, "%n%d students, %.0f%% separated, %d s, %d classes x %d runs%n", students,
        separations * 100, SECONDS, classes, repeats);
    double[] reference = classAverages(costs.values().iterator().next(), repeats);
    for (Map.Entry<String, List<Double>> entry : costs.entrySet()) {
      List<Double> values = entry.getValue();
      double[] perClass = classAverages(values, repeats);
      int better = 0;
      double difference = 0;
      for (int index = 0; index < perClass.length; index++) {
        better += perClass[index] < reference[index] ? 1 : 0;
        difference += (perClass[index] - reference[index]) / reference[index] / perClass.length;
      }
      System.out.printf(Locale.ROOT, "  %-12s average %.3f  worst %.3f  best %.3f  vs first: better on %d/%d classes,"
          + " %+.1f%%%n", entry.getKey(), values.stream().mapToDouble(Double::doubleValue).average().orElseThrow(),
          values.stream().mapToDouble(Double::doubleValue).max().orElseThrow(),
          values.stream().mapToDouble(Double::doubleValue).min().orElseThrow(), better, perClass.length,
          difference * 100);
    }
  }

  private static double[] classAverages(List<Double> values, int repeats) {
    double[] averages = new double[values.size() / repeats];
    for (int index = 0; index < averages.length; index++) {
      averages[index] = values.subList(index * repeats, (index + 1) * repeats).stream()
          .mapToDouble(Double::doubleValue).average().orElseThrow();
    }
    return averages;
  }

}
