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

// not a test: compares the algorithms on the same generated classes. Run it with ./mvnw test -Dtest=MatchingBenchmark
class MatchingBenchmark {

  // about 5 minutes: at 20 students everything ties, so only the sizes where the algorithms differ
  private static final int[] POOL_SIZES = { 40, 80 };
  private static final double[] SEPARATION_SHARES = { 0.05 };
  private static final int SEEDS = 5;
  private static final int[] SECONDS = { 1, 5, 10 };
  private static final int CORES = Runtime.getRuntime().availableProcessors();

  private record Run(String algorithm, int students, double separations, long seed, int seconds, double cost,
      boolean valid, long evaluations, long millis) {
  }

  @Test
  void compareAlgorithms() throws IOException {
    Map<String, MatchingAlgorithm> algorithms = new LinkedHashMap<>();
    algorithms.put("annealing", new SimulatedAnnealingMatching());
    algorithms.put("ants", new AntColonyMatching());
    compare(algorithms, false, Path.of("target", "matching-benchmark.csv"));
  }

  // on the clock, so it needs a quiet machine; run it with -Dtest=MatchingBenchmark#compareParallel
  @Test
  void compareParallel() throws IOException {
    Map<String, MatchingAlgorithm> algorithms = new LinkedHashMap<>();
    algorithms.put("annealing x" + CORES, new ParallelRestarts(SimulatedAnnealingMatching::new, CORES));
    // one ant per core, so both use every core
    AntColonyMatching.Settings tuned = AntColonyMatching.DEFAULTS;
    AntColonyMatching.Settings onePerCore = tuned.withAnts(CORES);
    algorithms.put("parallel ants", new AntColonyMatching(onePerCore, true));
    compare(algorithms, true, Path.of("target", "matching-benchmark-parallel.csv"));
  }

  // is the pheromone doing anything? the same ants with pheromone weight 0 build guided by the cost only
  @Test
  void comparePheromone() throws IOException {
    AntColonyMatching.Settings tuned = AntColonyMatching.DEFAULTS;
    AntColonyMatching.Settings blind = tuned.withPheromonePower(0);
    Map<String, MatchingAlgorithm> algorithms = new LinkedHashMap<>();
    algorithms.put("ants", new AntColonyMatching());
    algorithms.put("no pheromone", new AntColonyMatching(blind));
    compare(algorithms, false, Path.of("target", "matching-benchmark-pheromone.csv"));
  }

  // annealing sometimes gets stuck on a class, so every class runs a few times: the average and the worst run count
  @Test
  void compareRobustness() throws IOException {
    robustness(0.05, Path.of("target", "matching-benchmark-robustness.csv"));
  }

  // the same with tight constraints: 15% of the pairs separated, like a teacher who keeps many students apart
  @Test
  void compareHard() throws IOException {
    robustness(0.15, Path.of("target", "matching-benchmark-hard.csv"));
  }

  private static void robustness(double separations, Path output) throws IOException {
    // pure ACO: the best of the third tuning round without a local search temperature
    AntColonyMatching.Settings pure = new AntColonyMatching.Settings(10, 1, 1, 0.2, 0.01, 10, 20, 1, 0,
        new LocalSearch.Settings(20_000, 0, 0.25, 0, 0, 0, 20));
    // tabu as the ants' local search, with the sample size that made tabu work on its own
    AntColonyMatching.Settings withTabu = pure.withLocalSearch(pure.localSearch().withTabu(5, 1_000)
        .withSteps(100_000));
    Map<String, MatchingAlgorithm> algorithms = new LinkedHashMap<>();
    algorithms.put("annealing", new SimulatedAnnealingMatching());
    algorithms.put("ants", new AntColonyMatching(pure));
    algorithms.put("ants, hybrid", new AntColonyMatching());
    algorithms.put("ants + tabu", new AntColonyMatching(withTabu));
    algorithms.put("tabu search", new TabuSearchMatching());
    arena(algorithms, separations, SEEDS, output);
  }

  // the algorithms from the table of metaheuristics against annealing and the ants
  @Test
  void compareTable() throws IOException {
    arena(tableAlgorithms(), 0.05, SEEDS, Path.of("target", "matching-benchmark-table.csv"));
  }

  @Test
  void compareTableHard() throws IOException {
    arena(tableAlgorithms(), 0.15, SEEDS, Path.of("target", "matching-benchmark-table-hard.csv"));
  }

  // the long validation of the best ones, on classes none of them saw before (seeds 201-210): two class sizes,
  // normal and tight separations, 10 classes x 3 runs each
  // split in two so they can run at the same time; same classes and run seeds, so the CSVs can be put together
  @Test
  void compareFinalReference() throws IOException {
    Map<String, MatchingAlgorithm> algorithms = new LinkedHashMap<>();
    algorithms.put("annealing", new SimulatedAnnealingMatching());
    algorithms.put("ants, hybrid", new AntColonyMatching());
    algorithms.put("late acceptance", new LateAcceptanceMatching());
    finalRun("reference", algorithms);
  }

  @Test
  void compareFinalNature() throws IOException {
    Map<String, MatchingAlgorithm> algorithms = new LinkedHashMap<>();
    algorithms.put("bee colony", new BeeColonyMatching());
    algorithms.put("spider monkey", new SpiderMonkeyMatching());
    algorithms.put("chicken swarm", new ChickenSwarmMatching());
    finalRun("nature", algorithms);
  }

  // the finalists on 40 students, about the size of one attendance tier once a class with history is split
  @Test
  void compareFinalTierSize() throws IOException {
    Map<String, MatchingAlgorithm> algorithms = new LinkedHashMap<>();
    algorithms.put("annealing", new SimulatedAnnealingMatching());
    algorithms.put("ants, hybrid", new AntColonyMatching());
    algorithms.put("late acceptance", new LateAcceptanceMatching());
    algorithms.put("bee colony", new BeeColonyMatching());
    algorithms.put("spider monkey", new SpiderMonkeyMatching());
    algorithms.put("chicken swarm", new ChickenSwarmMatching());
    arena(algorithms, 40, 0.05, 201, 10, Path.of("target", "matching-final-tier-40-5.csv"));
  }

  // between the 40 students where the colonies edge ahead and the 80-120 where annealing does: where do they cross
  @Test
  void compareFinalCrossover() throws IOException {
    Map<String, MatchingAlgorithm> algorithms = new LinkedHashMap<>();
    algorithms.put("annealing", new SimulatedAnnealingMatching());
    algorithms.put("ants, hybrid", new AntColonyMatching());
    algorithms.put("bee colony", new BeeColonyMatching());
    arena(algorithms, 60, 0.05, 201, 10, Path.of("target", "matching-final-crossover-60-5.csv"));
  }

  // the bee colony and the hybrid bee colony against annealing and the hybrid ants; a shorter check:
  // 5 unseen classes x 2 runs per size
  @Test
  void compareBeeColony() throws IOException {
    beeColonyCheck(0.05);
  }

  @Test
  void compareBeeColonyHard() throws IOException {
    beeColonyCheck(0.15);
  }

  private static void beeColonyCheck(double separations) throws IOException {
    Map<String, MatchingAlgorithm> algorithms = new LinkedHashMap<>();
    algorithms.put("annealing", new SimulatedAnnealingMatching());
    algorithms.put("ants, hybrid", new AntColonyMatching());
    algorithms.put("bee colony", new BeeColonyMatching());
    algorithms.put("bee colony, hybrid", new BeeColonyMatching(BeeColonyMatching.DEFAULTS.withTemperature(0.2)));
    for (int students : new int[] { 40, 80, 120 }) {
      arena(algorithms, students, separations, 201, 5, 2, Path.of("target",
          "matching-bee-" + students + "-" + Math.round(separations * 100) + ".csv"));
    }
  }

  private static void finalRun(String label, Map<String, MatchingAlgorithm> algorithms) throws IOException {
    for (int students : new int[] { 80, 120 }) {
      for (double separations : new double[] { 0.05, 0.15 }) {
        arena(algorithms, students, separations, 201, 10, Path.of("target",
            "matching-final-" + label + "-" + students + "-" + Math.round(separations * 100) + ".csv"));
      }
    }
  }

  private static Map<String, MatchingAlgorithm> tableAlgorithms() {
    Map<String, MatchingAlgorithm> algorithms = new LinkedHashMap<>();
    algorithms.put("annealing", new SimulatedAnnealingMatching());
    algorithms.put("ants, hybrid", new AntColonyMatching());
    algorithms.put("late acceptance", new LateAcceptanceMatching());
    algorithms.put("bee colony", new BeeColonyMatching());
    algorithms.put("cuckoo search", new CuckooSearchMatching());
    algorithms.put("clonal selection", new ClonalSelectionMatching());
    algorithms.put("chemical reaction", new ChemicalReactionMatching());
    algorithms.put("particle swarm", new ParticleSwarmMatching());
    algorithms.put("black hole", new BlackHoleMatching());
    algorithms.put("bees algorithm", new BeesAlgorithmMatching());
    algorithms.put("shuffled frogs", new ShuffledFrogLeapingMatching());
    algorithms.put("invasive weed", new InvasiveWeedMatching());
    algorithms.put("grey wolf", new GreyWolfMatching());
    algorithms.put("great deluge", new GreatDelugeMatching());
    algorithms.put("firefly", new FireflyMatching());
    algorithms.put("whale", new WhaleMatching());
    algorithms.put("bacterial foraging", new BacterialForagingMatching());
    algorithms.put("imperialist", new ImperialistCompetitiveMatching());
    algorithms.put("chicken swarm", new ChickenSwarmMatching());
    algorithms.put("spider monkey", new SpiderMonkeyMatching());
    algorithms.put("iterated local", new IteratedLocalSearchMatching());
    algorithms.put("variable neighbour", new VariableNeighbourhoodMatching());
    return algorithms;
  }

  private static void arena(Map<String, MatchingAlgorithm> algorithms, double separations, int classes, Path output)
      throws IOException {
    arena(algorithms, 80, separations, 1, classes, output);
  }

  // every class several times, 10 s each; the first algorithm is the one the others are compared to
  private static void arena(Map<String, MatchingAlgorithm> algorithms, int students, double separations,
      long firstSeed, int classes, Path output) throws IOException {
    arena(algorithms, students, separations, firstSeed, classes, 3, output);
  }

  private static void arena(Map<String, MatchingAlgorithm> algorithms, int students, double separations,
      long firstSeed, int classes, int repeats, Path output) throws IOException {
    int seconds = 10;

    List<String> lines = new ArrayList<>(List.of("algorithm,class,run,cost"));
    Map<String, List<Double>> costs = new LinkedHashMap<>();
    for (long seed = firstSeed; seed < firstSeed + classes; seed++) {
      MatchingProblem problem = MatchingTestData.randomProblem(students, separations, seed, 3, 4, 5);
      for (int repeat = 1; repeat <= repeats; repeat++) {
        for (Map.Entry<String, MatchingAlgorithm> algorithm : algorithms.entrySet()) {
          MatchingBudget budget = MatchingBudget.cpuTime(Duration.ofSeconds(seconds));
          Grouping grouping = algorithm.getValue().solve(problem, budget, new Random(seed * 100 + repeat));
          double cost = MatchingCost.cost(problem, grouping);
          costs.computeIfAbsent(algorithm.getKey(), name -> new ArrayList<>()).add(cost);
          lines.add(String.format(Locale.ROOT, "%s,%d,%d,%.4f", algorithm.getKey(), seed, repeat, cost));
        }
      }
    }
    Files.write(output, lines);

    System.out.printf(Locale.ROOT, "%n%d students, %.0f%% separated, %d s, %d classes x %d runs%n", students,
        separations * 100, seconds, classes, repeats);
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
      System.out.printf(Locale.ROOT, "  %-18s average %.3f  worst %.3f  best %.3f  vs first: better on %d/%d classes,"
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

  private static void compare(Map<String, MatchingAlgorithm> algorithms, boolean wallClock, Path output)
      throws IOException {
    List<Run> runs = new ArrayList<>();
    for (int students : POOL_SIZES) {
      for (double separations : SEPARATION_SHARES) {
        for (long seed = 1; seed <= SEEDS; seed++) {
          MatchingProblem problem = MatchingTestData.randomProblem(students, separations, seed, 3, 4, 5);
          // greedy doesn't use the budget, once is enough
          runs.add(run("greedy", new GreedyMatching(), problem, separations, seed, 0, wallClock));
          for (int seconds : SECONDS) {
            for (Map.Entry<String, MatchingAlgorithm> algorithm : algorithms.entrySet()) {
              runs.add(run(algorithm.getKey(), algorithm.getValue(), problem, separations, seed, seconds, wallClock));
            }
          }
        }
      }
    }
    write(runs, output);
    printSummary(runs, algorithms.keySet());
  }

  private static Run run(String name, MatchingAlgorithm algorithm, MatchingProblem problem, double separations,
      long seed, int seconds, boolean wallClock) {
    // processor time, so whatever else runs on the machine doesn't change the results;
    // parallel runs need the clock, since processor time only counts one thread
    Duration limit = Duration.ofSeconds(seconds);
    MatchingBudget budget = wallClock ? MatchingBudget.time(limit) : MatchingBudget.cpuTime(limit);
    long start = System.nanoTime();
    Grouping grouping = algorithm.solve(problem, budget, new Random(seed));
    long millis = (System.nanoTime() - start) / 1_000_000;
    return new Run(name, problem.size(), separations, seed, seconds, MatchingCost.cost(problem, grouping),
        MatchingCost.isValid(problem, grouping), budget.getUsed(), millis);
  }

  private static void write(List<Run> runs, Path output) throws IOException {
    List<String> lines = new ArrayList<>();
    lines.add("algorithm,students,separations,seed,seconds,cost,valid,evaluations,millis");
    for (Run run : runs) {
      lines.add(String.format(Locale.ROOT, "%s,%d,%.2f,%d,%d,%.4f,%b,%d,%d", run.algorithm(), run.students(),
          run.separations(), run.seed(), run.seconds(), run.cost(), run.valid(), run.evaluations(), run.millis()));
    }
    Files.write(output, lines);
  }

  // per class type and budget: average cost (greedy as the floor) and how often each one had the lowest cost
  private static void printSummary(List<Run> runs, Iterable<String> names) {
    for (int students : POOL_SIZES) {
      for (double separations : SEPARATION_SHARES) {
        double greedy = average(runs, "greedy", students, separations, 0);
        System.out.printf(Locale.ROOT, "%n%d students, %.0f%% separated (greedy %.3f)%n", students,
            separations * 100, greedy);
        for (int seconds : SECONDS) {
          StringBuilder line = new StringBuilder(String.format(Locale.ROOT, "  %2d s:", seconds));
          for (String name : names) {
            line.append(String.format(Locale.ROOT, "  %s %.3f (best %d/%d)", name,
                average(runs, name, students, separations, seconds),
                wins(runs, name, students, separations, seconds), SEEDS));
          }
          System.out.println(line);
        }
      }
    }
  }

  private static List<Run> matching(List<Run> runs, String name, int students, double separations, int seconds) {
    return runs.stream().filter(run -> run.algorithm().equals(name) && run.students() == students
        && run.separations() == separations && run.seconds() == seconds).toList();
  }

  private static double average(List<Run> runs, String name, int students, double separations, int seconds) {
    return matching(runs, name, students, separations, seconds).stream().mapToDouble(Run::cost).average()
        .orElse(Double.NaN);
  }

  private static long wins(List<Run> runs, String name, int students, double separations, int seconds) {
    long wins = 0;
    for (long seed = 1; seed <= SEEDS; seed++) {
      long current = seed;
      List<Run> sameClass = runs.stream().filter(run -> run.students() == students
          && run.separations() == separations && run.seed() == current && run.seconds() == seconds).toList();
      double lowest = sameClass.stream().mapToDouble(Run::cost).min().orElse(Double.NaN);
      wins += sameClass.stream().anyMatch(run -> run.algorithm().equals(name) && run.cost() == lowest) ? 1 : 0;
    }
    return wins;
  }

}
