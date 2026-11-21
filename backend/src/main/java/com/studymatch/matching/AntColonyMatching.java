package com.studymatch.matching;

import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;

// MAX-MIN Ant System: ants build groupings, the round's best is polished by a local search and leaves pheromone on
// the pairs it put together, and later ants follow it
public final class AntColonyMatching implements MatchingAlgorithm {

  // ants: per round; powers: how much the pheromone and the cost increase weigh when an ant picks a group;
  // evaporation, minPheromone: MAX-MIN pheromone; globalBestEvery: every this many rounds the best so far leaves the
  // pheromone instead of the round's best; stagnationRounds: rounds without a new best before the pheromone resets;
  // fairnessWeight: how much an ant looks at the group averages while building; localEvaporation: Ant Colony
  // System fade (0 is off); localSearch: how each grouping is polished
  public record Settings(int ants, double pheromonePower, double heuristicPower, double evaporation,
      double minPheromone, int globalBestEvery, int stagnationRounds, double fairnessWeight, double localEvaporation,
      LocalSearch.Settings localSearch) {

    public Settings withAnts(int value) {
      return new Settings(value, pheromonePower, heuristicPower, evaporation, minPheromone, globalBestEvery,
          stagnationRounds, fairnessWeight, localEvaporation, localSearch);
    }

    public Settings withPheromonePower(double value) {
      return new Settings(ants, value, heuristicPower, evaporation, minPheromone, globalBestEvery, stagnationRounds,
          fairnessWeight, localEvaporation, localSearch);
    }

    public Settings withSchedule(int globalBest, int stagnation) {
      return new Settings(ants, pheromonePower, heuristicPower, evaporation, minPheromone, globalBest, stagnation,
          fairnessWeight, localEvaporation, localSearch);
    }

    public Settings withFairnessWeight(double value) {
      return new Settings(ants, pheromonePower, heuristicPower, evaporation, minPheromone, globalBestEvery,
          stagnationRounds, value, localEvaporation, localSearch);
    }

    public Settings withLocalEvaporation(double value) {
      return new Settings(ants, pheromonePower, heuristicPower, evaporation, minPheromone, globalBestEvery,
          stagnationRounds, fairnessWeight, value, localSearch);
    }

    Settings withLocalSearch(LocalSearch.Settings value) {
      return new Settings(ants, pheromonePower, heuristicPower, evaporation, minPheromone, globalBestEvery,
          stagnationRounds, fairnessWeight, localEvaporation, value);
    }

  }

  // the hybrid from the fourth tuning round: level with annealing on average, better worst case on typical classes
  public static final Settings DEFAULTS = new Settings(10, 1, 1, 0.2, 0.01, 10, 50, 4, 0,
      new LocalSearch.Settings(40_000, 0, 0.5, 0.2, 0, 0, 20));

  // rounds in a row where every ant hit a dead end before giving up
  static final int MAX_EMPTY_ROUNDS = 20;

  private final GreedyMatching greedy = new GreedyMatching();
  private final Settings settings;
  private final LocalSearch localSearch;
  // the ants of a round build and polish at the same time on different cores
  private final boolean parallel;

  public AntColonyMatching() {
    this(DEFAULTS, false);
  }

  public AntColonyMatching(Settings settings) {
    this(settings, false);
  }

  public AntColonyMatching(Settings settings, boolean parallel) {
    this.settings = settings;
    this.localSearch = new LocalSearch(settings.localSearch());
    this.parallel = parallel;
  }

  @Override
  public Grouping solve(MatchingProblem problem, MatchingBudget budget, Random random) {
    // processor time only counts this thread, the other cores would work for free
    if (parallel && budget.isCpuTime()) {
      throw new IllegalStateException("parallel ants are measured on the clock, not on processor time");
    }
    Pheromone pheromone = new Pheromone(problem.size(), settings.minPheromone());
    Ant ant = new Ant(problem, pheromone, settings, settings.localEvaporation() > 0 && !parallel);
    Progress progress = new Progress();
    int emptyRounds = 0;

    while (!budget.isExhausted()) {
      TrackedCost champion = parallel ? parallelRound(problem, ant, budget, random)
          : sequentialRound(problem, ant, budget, random);
      if (champion == null) {
        if (++emptyRounds >= MAX_EMPTY_ROUNDS) {
          throw new IllegalStateException("the separations leave no valid grouping");
        }
        continue;
      }
      emptyRounds = 0;
      learn(pheromone, progress, champion.grouping(), champion.total());
    }
    // the budget ran out before any ant finished
    return progress.best != null ? progress.best : greedy.solve(problem, budget, random);
  }

  // the best grouping so far and how the rounds went, for the pheromone schedule
  private static final class Progress {

    private Grouping best;
    private double bestCost = Double.POSITIVE_INFINITY;
    private int rounds;
    private int roundsWithoutBetter;

  }

  // the round's best (or every few rounds the best so far) leaves pheromone; a long run without a new best resets it
  private void learn(Pheromone pheromone, Progress progress, Grouping roundBest, double roundBestCost) {
    if (roundBestCost < progress.bestCost) {
      progress.best = roundBest;
      progress.bestCost = roundBestCost;
      progress.roundsWithoutBetter = 0;
    } else {
      progress.roundsWithoutBetter++;
    }
    boolean fromBest = ++progress.rounds % settings.globalBestEvery() == 0;
    pheromone.reinforce(fromBest ? progress.best : roundBest, fromBest ? progress.bestCost : roundBestCost,
        settings.evaporation());
    if (progress.roundsWithoutBetter >= settings.stagnationRounds()) {
      pheromone.reset();
      progress.roundsWithoutBetter = 0;
    }
  }

  // ants build one by one and only the round's best is polished, everything charged as it happens
  private TrackedCost sequentialRound(MatchingProblem problem, Ant ant, MatchingBudget budget, Random random) {
    Grouping roundBest = null;
    double roundBestCost = Double.POSITIVE_INFINITY;
    for (int built = 0; built < settings.ants() && !budget.isExhausted(); built++) {
      int[] groupOf = ant.build(random);
      if (groupOf == null || !budget.spend()) {
        continue;
      }
      Grouping grouping = new Grouping(groupOf, problem.getGroupCount());
      double cost = new TrackedCost(problem, grouping).total();
      if (cost < roundBestCost) {
        roundBest = grouping;
        roundBestCost = cost;
      }
    }
    return roundBest == null ? null : localSearch.polish(problem, roundBest, random, budget::spend).polished();
  }

  // every ant builds and polishes on its own core; the work is charged afterwards, in ant order
  private TrackedCost parallelRound(MatchingProblem problem, Ant ant, MatchingBudget budget, Random random) {
    // one seed per ant drawn first, so the groups don't depend on which thread is faster
    long[] seeds = random.longs(settings.ants()).toArray();
    List<LocalSearch.Outcome> ants = IntStream.range(0, settings.ants()).parallel()
        .mapToObj(index -> {
          Random antRandom = new Random(seeds[index]);
          int[] groupOf = ant.build(antRandom);
          return groupOf == null ? null : localSearch.polish(problem,
              new Grouping(groupOf, problem.getGroupCount()), antRandom, () -> true);
        })
        .toList();

    LocalSearch.Outcome best = null;
    for (LocalSearch.Outcome outcome : ants) {
      if (outcome == null) {
        continue;
      }
      // the ant's own cost plus every step it tried
      charge(budget, 1 + outcome.evaluations());
      if (best == null || outcome.polished().total() < best.polished().total()) {
        best = outcome;
      }
    }
    return best == null ? null : best.polished();
  }

  // work done on other cores, counted once it's finished
  private static void charge(MatchingBudget budget, int evaluations) {
    for (int i = 0; i < evaluations; i++) {
      if (!budget.spend()) {
        return;
      }
    }
  }

}
