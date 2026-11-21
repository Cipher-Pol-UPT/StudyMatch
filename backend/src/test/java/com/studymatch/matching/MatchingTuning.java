package com.studymatch.matching;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;

// not a test: tries settings for the ants and for annealing. Run it with ./mvnw test -Dtest=MatchingTuning
class MatchingTuning {

  private static final int[] POOL_SIZES = { 40, 80 };
  // other classes than the benchmark's (seeds 1-5), so the tuning doesn't fit the classes we score on
  private static final long[] SEEDS = { 101, 102, 103 };
  private static final Duration BUDGET = Duration.ofSeconds(5);
  private static final int ANT_SAMPLES = 24;

  private record Result(String settings, double cost) {
  }

  // the settings each round started from, so every round can be run again as it was
  private static final AntColonyMatching.Settings AFTER_ROUND_TWO = colony(10, 1, 1, 0.2, 5, 20, 0,
      localSearch(20_000, 0.5, 0));
  private static final AntColonyMatching.Settings PURE_AFTER_ROUND_THREE = colony(10, 1, 1, 0.2, 10, 20, 1,
      localSearch(20_000, 0.25, 0));

  private static AntColonyMatching.Settings colony(int ants, double pheromonePower, double heuristicPower,
      double evaporation, int globalBestEvery, int stagnationRounds, double fairnessWeight,
      LocalSearch.Settings localSearch) {
    return new AntColonyMatching.Settings(ants, pheromonePower, heuristicPower, evaporation, 0.01, globalBestEvery,
        stagnationRounds, fairnessWeight, 0, localSearch);
  }

  // swaps only, no rotations, no tabu
  private static LocalSearch.Settings localSearch(int steps, double targetedShare, double temperature) {
    return new LocalSearch.Settings(steps, 0, targetedShare, temperature, 0, 0, 20);
  }

  @Test
  void tuneAnts() {
    // the first defaults, then random picks from the grid
    Set<AntColonyMatching.Settings> candidates = new LinkedHashSet<>();
    candidates.add(colony(10, 1, 2, 0.1, 5, 20, 0, localSearch(200, 0.5, 0)));
    Random random = new Random(2026);
    int[] ants = { 5, 10, 20 };
    double[] pheromonePowers = { 1, 2 };
    double[] heuristicPowers = { 1, 2, 4 };
    double[] evaporations = { 0.05, 0.1, 0.2 };
    int[] localSearchSteps = { 200, 1_000, 5_000 };
    while (candidates.size() < ANT_SAMPLES) {
      candidates.add(colony(ants[random.nextInt(ants.length)], pheromonePowers[random.nextInt(pheromonePowers.length)],
          heuristicPowers[random.nextInt(heuristicPowers.length)], evaporations[random.nextInt(evaporations.length)],
          5, 20, 0, localSearch(localSearchSteps[random.nextInt(localSearchSteps.length)], 0.5, 0)));
    }

    List<Result> results = new ArrayList<>();
    for (AntColonyMatching.Settings settings : candidates) {
      results.add(new Result(settings.toString(), averageCost(() -> new AntColonyMatching(settings))));
    }
    print("ants", results);
  }

  // second round: the first one's best used the longest local search tried, so try longer on its top three
  @Test
  void tuneAntLocalSearch() {
    List<AntColonyMatching.Settings> top = List.of(colony(5, 2, 1, 0.2, 5, 20, 0, localSearch(0, 0.5, 0)),
        colony(10, 1, 2, 0.05, 5, 20, 0, localSearch(0, 0.5, 0)), colony(10, 1, 1, 0.2, 5, 20, 0, localSearch(0, 0.5, 0)));
    List<Result> results = new ArrayList<>();
    for (AntColonyMatching.Settings base : top) {
      for (int steps : new int[] { 5_000, 10_000, 20_000, 50_000 }) {
        AntColonyMatching.Settings settings = base.withLocalSearch(base.localSearch().withSteps(steps));
        results.add(new Result(settings.toString(), averageCost(() -> new AntColonyMatching(settings))));
      }
    }
    print("ants, local search length", results);
  }

  // third round: the knobs that were picked by hand, plus fairness while building and a local search temperature
  @Test
  void tuneAntExtras() {
    Set<AntColonyMatching.Settings> candidates = new LinkedHashSet<>();
    candidates.add(AFTER_ROUND_TWO);
    Random random = new Random(2027);
    double[] targeted = { 0.25, 0.5, 0.75 };
    int[] globalBest = { 2, 5, 10 };
    int[] stagnation = { 10, 20, 50 };
    double[] fairness = { 0, 0.5, 1, 2 };
    double[] temperatures = { 0, 0.05, 0.2 };
    while (candidates.size() < 15) {
      LocalSearch.Settings search = AFTER_ROUND_TWO.localSearch()
          .withTargetedShare(targeted[random.nextInt(targeted.length)]);
      candidates.add(AFTER_ROUND_TWO
          .withSchedule(globalBest[random.nextInt(globalBest.length)], stagnation[random.nextInt(stagnation.length)])
          .withFairnessWeight(fairness[random.nextInt(fairness.length)])
          .withLocalSearch(search.withTemperature(temperatures[random.nextInt(temperatures.length)])));
    }

    List<Result> results = new ArrayList<>();
    for (AntColonyMatching.Settings settings : candidates) {
      results.add(new Result(settings.toString(), averageCost(() -> new AntColonyMatching(settings))));
    }
    print("ants, extras", results);
  }

  // fourth round: the hybrid's best temperature and fairness were the largest tried, so go further
  @Test
  void tuneAntHybrid() {
    AntColonyMatching.Settings base = colony(10, 1, 1, 0.2, 10, 50, 2, localSearch(20_000, 0.5, 0.2));
    List<Result> results = new ArrayList<>();
    for (double temperature : new double[] { 0.2, 0.5, 1.0 }) {
      for (double fairness : new double[] { 2, 4 }) {
        for (int steps : new int[] { 20_000, 40_000 }) {
          AntColonyMatching.Settings settings = base.withFairnessWeight(fairness)
              .withLocalSearch(base.localSearch().withTemperature(temperature).withSteps(steps));
          results.add(new Result(settings.toString(), averageCost(() -> new AntColonyMatching(settings))));
        }
      }
    }
    print("ants, hybrid", results);
  }

  // fifth round: tabu search, three way rotations and Ant Colony System fading, alone and together
  @Test
  void tuneAntNewMoves() {
    AntColonyMatching.Settings pure = PURE_AFTER_ROUND_THREE;
    LocalSearch.Settings search = pure.localSearch();
    Map<String, AntColonyMatching.Settings> candidates = new LinkedHashMap<>();
    candidates.put("pure ACO (after round 3)", pure);
    candidates.put("hybrid (round 4)", AntColonyMatching.DEFAULTS);
    for (int tenure : new int[] { 5, 10, 20 }) {
      candidates.put("tabu " + tenure, pure.withLocalSearch(search.withTabu(tenure, 20)));
    }
    for (double rotations : new double[] { 0.25, 0.5 }) {
      candidates.put("rotations " + rotations, pure.withLocalSearch(search.withRotationShare(rotations)));
    }
    for (double fade : new double[] { 0.05, 0.1 }) {
      candidates.put("local fading " + fade, pure.withLocalEvaporation(fade));
    }
    candidates.put("tabu 10 + rotations 0.25",
        pure.withLocalSearch(search.withTabu(10, 20).withRotationShare(0.25)));
    candidates.put("tabu 10 + fading 0.1", pure.withLocalSearch(search.withTabu(10, 20)).withLocalEvaporation(0.1));
    candidates.put("tabu 10 + rotations 0.25 + fading 0.1",
        pure.withLocalSearch(search.withTabu(10, 20).withRotationShare(0.25)).withLocalEvaporation(0.1));

    List<Result> results = new ArrayList<>();
    for (Map.Entry<String, AntColonyMatching.Settings> candidate : candidates.entrySet()) {
      results.add(new Result(candidate.getKey(), averageCost(() -> new AntColonyMatching(candidate.getValue()))));
    }
    print("ants, new moves", results);
  }

  // tabu search on its own; a sample of 20 swaps per step made the ants' tabu wander uphill, so bigger samples too
  @Test
  void tuneTabu() {
    List<Result> results = new ArrayList<>();
    for (int sample : new int[] { 20, 200, 1_000 }) {
      for (int tenure : new int[] { 5, 10 }) {
        TabuSearchMatching.Settings settings = new TabuSearchMatching.Settings(tenure, sample, 0.25, 200);
        results.add(new Result(settings.toString(), averageCost(() -> new TabuSearchMatching(settings))));
      }
    }
    print("tabu search", results);
  }

  // the algorithms from the table of metaheuristics, each tuned about as much as annealing (4-6 settings)
  @Test
  void tuneTable() {
    Map<String, Supplier<MatchingAlgorithm>> candidates = new LinkedHashMap<>();
    for (int history : new int[] { 100, 500, 1_000, 2_000, 5_000, 20_000 }) {
      candidates.put("late acceptance " + history,
          () -> new LateAcceptanceMatching(new LateAcceptanceMatching.Settings(history)));
    }
    for (int sources : new int[] { 10, 20, 40 }) {
      for (int limit : new int[] { 100, 1_000 }) {
        candidates.put("bee colony " + sources + " sources, limit " + limit,
            () -> new BeeColonyMatching(new BeeColonyMatching.Settings(sources, limit, 0.5, 0.5)));
      }
    }
    for (int nests : new int[] { 10, 25 }) {
      for (double abandon : new double[] { 0.1, 0.25, 0.5 }) {
        candidates.put("cuckoo " + nests + " nests, abandon " + abandon,
            () -> new CuckooSearchMatching(new CuckooSearchMatching.Settings(nests, abandon, 1)));
      }
    }
    for (double cloneFactor : new double[] { 0.25, 0.5, 1 }) {
      for (double decay : new double[] { 1, 3 }) {
        candidates.put("clonal clones " + cloneFactor + ", decay " + decay,
            () -> new ClonalSelectionMatching(new ClonalSelectionMatching.Settings(20, 10, cloneFactor, decay, 2)));
      }
    }
    for (double kinetic : new double[] { 0.5, 2 }) {
      for (int decomposeAfter : new int[] { 50, 500 }) {
        candidates.put("chemical kinetic " + kinetic + ", decompose after " + decomposeAfter,
            () -> new ChemicalReactionMatching(
                new ChemicalReactionMatching.Settings(10, kinetic, 0.2, 0.2, decomposeAfter, 0.05)));
      }
    }
    for (double keLoss : new double[] { 0.1, 0.5 }) {
      candidates.put("chemical ke loss " + keLoss,
          () -> new ChemicalReactionMatching(new ChemicalReactionMatching.Settings(10, 1, keLoss, 0.2, 100, 0.05)));
    }
    for (int particles : new int[] { 10, 30 }) {
      for (double maxVelocity : new double[] { 0.05, 0.2, 0.5 }) {
        candidates.put("particle swarm " + particles + ", max velocity " + maxVelocity,
            () -> new ParticleSwarmMatching(
                new ParticleSwarmMatching.Settings(particles, 0.7298, 1.49618, 1.49618, maxVelocity)));
      }
    }
    for (int stars : new int[] { 10, 20, 40, 80 }) {
      candidates.put("black hole " + stars + " stars",
          () -> new BlackHoleMatching(new BlackHoleMatching.Settings(stars)));
    }

    List<Result> results = new ArrayList<>();
    for (Map.Entry<String, Supplier<MatchingAlgorithm>> candidate : candidates.entrySet()) {
      results.add(new Result(candidate.getKey(), averageCost(candidate.getValue())));
    }
    print("table of metaheuristics", results);
  }

  // the first wave's best values that were at the edge of what was tried, one step further
  @Test
  void tuneTableEdges() {
    Map<String, Supplier<MatchingAlgorithm>> candidates = new LinkedHashMap<>();
    for (int limit : new int[] { 1_000, 3_000, 10_000 }) {
      for (double partner : new double[] { 0.25, 0.5, 0.75 }) {
        candidates.put("bee colony limit " + limit + ", partner " + partner,
            () -> new BeeColonyMatching(new BeeColonyMatching.Settings(40, limit, partner, 0.5)));
      }
    }
    for (int history : new int[] { 5_000, 10_000 }) {
      candidates.put("late acceptance " + history,
          () -> new LateAcceptanceMatching(new LateAcceptanceMatching.Settings(history)));
    }
    for (double cloneFactor : new double[] { 1, 2 }) {
      for (double decay : new double[] { 3, 6 }) {
        candidates.put("clonal clones " + cloneFactor + ", decay " + decay,
            () -> new ClonalSelectionMatching(new ClonalSelectionMatching.Settings(20, 10, cloneFactor, decay, 2)));
      }
    }
    for (int decomposeAfter : new int[] { 500, 2_000, 10_000 }) {
      candidates.put("chemical decompose after " + decomposeAfter,
          () -> new ChemicalReactionMatching(new ChemicalReactionMatching.Settings(10, 2, 0.2, 0.2, decomposeAfter,
              0.05)));
    }

    List<Result> results = new ArrayList<>();
    for (Map.Entry<String, Supplier<MatchingAlgorithm>> candidate : candidates.entrySet()) {
      results.add(new Result(candidate.getKey(), averageCost(candidate.getValue())));
    }
    print("table of metaheuristics, edges", results);
  }

  // the best nature-inspired ones fell behind on 80 students in the arena (tuning mixed 40 and 80, and 40 is easy),
  // so they get a round on 80 students only, with annealing and late acceptance on the same classes for reference
  @Test
  void tuneNatureAt80() {
    Map<String, Supplier<MatchingAlgorithm>> candidates = new LinkedHashMap<>();
    candidates.put("annealing (reference)", SimulatedAnnealingMatching::new);
    candidates.put("late acceptance (reference)", LateAcceptanceMatching::new);
    for (int sources : new int[] { 10, 20, 40 }) {
      for (double moveShare : new double[] { 0, 0.5 }) {
        for (double partner : new double[] { 0.75, 0.9 }) {
          candidates.put("bee colony " + sources + " sources, moves " + moveShare + ", partner " + partner,
              () -> new BeeColonyMatching(new BeeColonyMatching.Settings(sources, 3_000, partner, moveShare)));
        }
      }
    }
    for (int monkeys : new int[] { 10, 20, 30 }) {
      for (int localLimit : new int[] { 50, 200 }) {
        candidates.put("spider monkeys " + monkeys + ", local limit " + localLimit,
            () -> new SpiderMonkeyMatching(new SpiderMonkeyMatching.Settings(monkeys, 4, localLimit, 20, 0.3)));
      }
    }
    for (int chickens : new int[] { 10, 20, 40 }) {
      for (int regroup : new int[] { 5, 20 }) {
        candidates.put("chickens " + chickens + ", regroup " + regroup,
            () -> new ChickenSwarmMatching(new ChickenSwarmMatching.Settings(chickens, 0.2, 0.6, regroup)));
      }
    }

    List<Result> results = new ArrayList<>();
    for (Map.Entry<String, Supplier<MatchingAlgorithm>> candidate : candidates.entrySet()) {
      results.add(new Result(candidate.getKey(), averageCost(candidate.getValue(), new int[] { 80 })));
    }
    print("nature-inspired at 80 students", results);
  }

  // the bee colony with what the ants had and it didn't: targeted steps, smarter scouts, rank-based onlookers
  @Test
  void tuneBeeColony() {
    Map<String, Supplier<MatchingAlgorithm>> candidates = new LinkedHashMap<>();
    candidates.put("annealing (reference)", SimulatedAnnealingMatching::new);
    candidates.put("ants, hybrid (reference)", AntColonyMatching::new);
    BeeColonyMatching.Settings base = BeeColonyMatching.DEFAULTS;
    for (double targeted : new double[] { 0, 0.25, 0.5 }) {
      for (int scoutKick : new int[] { 0, 5 }) {
        for (boolean ranked : new boolean[] { false, true }) {
          candidates.put("bee colony targeted " + targeted + ", scout kick " + scoutKick + ", ranked " + ranked,
              () -> new BeeColonyMatching(new BeeColonyMatching.Settings(base.sources(), base.limit(),
                  base.partnerShare(), base.moveShare(), targeted, scoutKick, ranked)));
        }
      }
    }

    List<Result> results = new ArrayList<>();
    for (Map.Entry<String, Supplier<MatchingAlgorithm>> candidate : candidates.entrySet()) {
      results.add(new Result(candidate.getKey(), averageCost(candidate.getValue(), new int[] { 80 })));
    }
    print("bee colony upgrades at 80 students", results);
  }

  // the upgrades didn't help, so the hybrid: bees that sometimes keep a worse change, like the hybrid ants
  @Test
  void tuneBeeHybrid() {
    Map<String, Supplier<MatchingAlgorithm>> candidates = new LinkedHashMap<>();
    candidates.put("annealing (reference)", SimulatedAnnealingMatching::new);
    candidates.put("ants, hybrid (reference)", AntColonyMatching::new);
    candidates.put("bee colony (reference)", BeeColonyMatching::new);
    for (double temperature : new double[] { 0.02, 0.05, 0.1, 0.2, 0.5 }) {
      candidates.put("bee colony, temperature " + temperature,
          () -> new BeeColonyMatching(BeeColonyMatching.DEFAULTS.withTemperature(temperature)));
    }

    List<Result> results = new ArrayList<>();
    for (Map.Entry<String, Supplier<MatchingAlgorithm>> candidate : candidates.entrySet()) {
      results.add(new Result(candidate.getKey(), averageCost(candidate.getValue(), new int[] { 80 })));
    }
    print("hybrid bee colony at 80 students", results);
  }

  // the bee colony's best partner share was the largest tried; one step further, and late acceptance around 5000
  @Test
  void tuneEdgesAt80() {
    Map<String, Supplier<MatchingAlgorithm>> candidates = new LinkedHashMap<>();
    candidates.put("annealing (reference)", SimulatedAnnealingMatching::new);
    for (int sources : new int[] { 5, 10 }) {
      for (double partner : new double[] { 0.9, 0.95, 1 }) {
        candidates.put("bee colony " + sources + " sources, partner " + partner,
            () -> new BeeColonyMatching(new BeeColonyMatching.Settings(sources, 3_000, partner, 0)));
      }
    }
    for (int history : new int[] { 2_000, 5_000, 8_000 }) {
      candidates.put("late acceptance " + history,
          () -> new LateAcceptanceMatching(new LateAcceptanceMatching.Settings(history)));
    }

    List<Result> results = new ArrayList<>();
    for (Map.Entry<String, Supplier<MatchingAlgorithm>> candidate : candidates.entrySet()) {
      results.add(new Result(candidate.getKey(), averageCost(candidate.getValue(), new int[] { 80 })));
    }
    print("edges at 80 students", results);
  }

  // the second wave from the table, tuned the same way
  @Test
  void tuneTableSecondWave() {
    Map<String, Supplier<MatchingAlgorithm>> candidates = new LinkedHashMap<>();
    for (int patch : new int[] { 3, 6, 12 }) {
      for (int sites : new int[] { 10, 20 }) {
        candidates.put("bees algorithm " + sites + " sites, patch " + patch,
            () -> new BeesAlgorithmMatching(new BeesAlgorithmMatching.Settings(sites, 6, 2, 8, 3, patch, 10)));
      }
    }
    for (int frogs : new int[] { 20, 50 }) {
      for (int maxLeap : new int[] { 2, 6 }) {
        candidates.put("frogs " + frogs + ", max leap " + maxLeap,
            () -> new ShuffledFrogLeapingMatching(new ShuffledFrogLeapingMatching.Settings(frogs, 5, 10, maxLeap)));
      }
    }
    for (double spread : new double[] { 4, 8, 16 }) {
      for (int maxSeeds : new int[] { 3, 8 }) {
        candidates.put("invasive weed spread " + spread + ", max seeds " + maxSeeds,
            () -> new InvasiveWeedMatching(new InvasiveWeedMatching.Settings(10, 20, 1, maxSeeds, spread)));
      }
    }
    for (int wolves : new int[] { 10, 20, 40, 80 }) {
      candidates.put("grey wolf " + wolves + " wolves",
          () -> new GreyWolfMatching(new GreyWolfMatching.Settings(wolves)));
    }
    for (double start : new double[] { 1, 1.2 }) {
      for (double end : new double[] { 0, 0.5, 0.8 }) {
        candidates.put("great deluge " + start + " to " + end,
            () -> new GreatDelugeMatching(new GreatDelugeMatching.Settings(start, end)));
      }
    }

    List<Result> results = new ArrayList<>();
    for (Map.Entry<String, Supplier<MatchingAlgorithm>> candidate : candidates.entrySet()) {
      results.add(new Result(candidate.getKey(), averageCost(candidate.getValue())));
    }
    print("table of metaheuristics, second wave", results);
  }

  // the third wave, tuned the same way
  @Test
  void tuneTableThirdWave() {
    Map<String, Supplier<MatchingAlgorithm>> candidates = new LinkedHashMap<>();
    for (double absorption : new double[] { 0.1, 1, 10 }) {
      for (double wander : new double[] { 0.02, 0.1 }) {
        candidates.put("firefly absorption " + absorption + ", wander " + wander,
            () -> new FireflyMatching(new FireflyMatching.Settings(15, 1, absorption, wander)));
      }
    }
    for (int whales : new int[] { 10, 20, 40, 80 }) {
      candidates.put("whale " + whales, () -> new WhaleMatching(new WhaleMatching.Settings(whales)));
    }
    for (int chemotaxis : new int[] { 20, 100 }) {
      for (double dispersal : new double[] { 0.1, 0.4 }) {
        candidates.put("bacteria chemotaxis " + chemotaxis + ", dispersal " + dispersal,
            () -> new BacterialForagingMatching(new BacterialForagingMatching.Settings(10, chemotaxis, 4, 4,
                dispersal)));
      }
    }
    for (int empires : new int[] { 3, 6 }) {
      for (double revolution : new double[] { 0.1, 0.3, 0.6 }) {
        candidates.put("imperialist " + empires + " empires, revolution " + revolution,
            () -> new ImperialistCompetitiveMatching(new ImperialistCompetitiveMatching.Settings(30, empires, 2,
                revolution)));
      }
    }
    for (int chickens : new int[] { 20, 40 }) {
      for (int regroup : new int[] { 5, 20 }) {
        candidates.put("chickens " + chickens + ", regroup " + regroup,
            () -> new ChickenSwarmMatching(new ChickenSwarmMatching.Settings(chickens, 0.2, 0.6, regroup)));
      }
    }
    for (int localLimit : new int[] { 10, 50 }) {
      for (int globalLimit : new int[] { 20, 100 }) {
        candidates.put("spider monkeys local " + localLimit + ", global " + globalLimit,
            () -> new SpiderMonkeyMatching(new SpiderMonkeyMatching.Settings(30, 4, localLimit, globalLimit, 0.3)));
      }
    }
    for (int kick : new int[] { 2, 5, 10 }) {
      for (int polish : new int[] { 1_000, 5_000 }) {
        candidates.put("iterated local search kick " + kick + ", polish " + polish,
            () -> new IteratedLocalSearchMatching(new IteratedLocalSearchMatching.Settings(kick, polish)));
      }
    }
    for (int maxShake : new int[] { 4, 8, 16 }) {
      for (int polish : new int[] { 1_000, 5_000 }) {
        candidates.put("variable neighbourhood shake " + maxShake + ", polish " + polish,
            () -> new VariableNeighbourhoodMatching(new VariableNeighbourhoodMatching.Settings(maxShake, polish)));
      }
    }

    List<Result> results = new ArrayList<>();
    for (Map.Entry<String, Supplier<MatchingAlgorithm>> candidate : candidates.entrySet()) {
      results.add(new Result(candidate.getKey(), averageCost(candidate.getValue())));
    }
    print("table of metaheuristics, third wave", results);
  }

  @Test
  void tuneAnnealing() {
    List<Result> results = new ArrayList<>();
    for (double start : new double[] { 0.3, 1, 3 }) {
      for (double end : new double[] { 0.01, 0.001, 0.0001 }) {
        SimulatedAnnealingMatching.Settings settings = new SimulatedAnnealingMatching.Settings(start, end);
        results.add(new Result(settings.toString(), averageCost(() -> new SimulatedAnnealingMatching(settings))));
      }
    }
    print("annealing", results);
  }

  private static double averageCost(Supplier<MatchingAlgorithm> algorithm) {
    return averageCost(algorithm, POOL_SIZES);
  }

  private static double averageCost(Supplier<MatchingAlgorithm> algorithm, int[] poolSizes) {
    double sum = 0;
    int runs = 0;
    for (int students : poolSizes) {
      for (long seed : SEEDS) {
        MatchingProblem problem = MatchingTestData.randomProblem(students, 0.05, seed, 3, 4, 5);
        Grouping grouping = algorithm.get().solve(problem, MatchingBudget.cpuTime(BUDGET), new Random(seed));
        sum += MatchingCost.cost(problem, grouping);
        runs++;
      }
    }
    return sum / runs;
  }

  private static void print(String name, List<Result> results) {
    System.out.printf(Locale.ROOT, "%n%s, best first:%n", name);
    results.stream().sorted((first, second) -> Double.compare(first.cost(), second.cost()))
        .forEach(result -> System.out.printf(Locale.ROOT, "  %.3f  %s%n", result.cost(), result.settings()));
  }

}
