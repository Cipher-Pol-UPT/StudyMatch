package com.studymatch.matching;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

// Cuckoo Search (Yang & Deb): every cuckoo lays an egg, a nest changed by a Levy flight (mostly short, now and then
// a long jump), into a random nest and it stays if it's better; the worst nests are abandoned and rebuilt
public final class CuckooSearchMatching implements MatchingAlgorithm {

  // nests: groupings kept; abandon: share of the worst nests rebuilt every generation;
  // flightScale: how many steps a Levy flight of length 1 is
  public record Settings(int nests, double abandon, double flightScale) {
  }

  // from MatchingTuning
  public static final Settings DEFAULTS = new Settings(25, 0.1, 1);

  // Mantegna's method for a Levy distribution with beta 1.5: sigma of the numerator
  private static final double BETA = 1.5;
  private static final double SIGMA = 0.6966;

  private final GreedyMatching greedy = new GreedyMatching();
  private final Settings settings;

  public CuckooSearchMatching() {
    this(DEFAULTS);
  }

  public CuckooSearchMatching(Settings settings) {
    this.settings = settings;
  }

  @Override
  public Grouping solve(MatchingProblem problem, MatchingBudget budget, Random random) {
    List<TrackedCost> nests = new ArrayList<>();
    for (int nest = 0; nest < settings.nests(); nest++) {
      nests.add(new TrackedCost(problem, greedy.solve(problem, budget, random)));
    }
    if (problem.getGroupCount() < 2) {
      return nests.getFirst().grouping();
    }
    Grouping best = null;
    double bestCost = Double.POSITIVE_INFINITY;

    while (!budget.isExhausted()) {
      for (int cuckoo = 0; cuckoo < nests.size() && !budget.isExhausted(); cuckoo++) {
        TrackedCost egg = new TrackedCost(problem, nests.get(cuckoo).grouping());
        fly(problem, egg, budget, random);
        int host = random.nextInt(nests.size());
        if (egg.total() < nests.get(host).total()) {
          nests.set(host, egg);
        }
      }
      nests.sort(Comparator.comparingDouble(TrackedCost::total));
      if (nests.getFirst().total() < bestCost) {
        best = nests.getFirst().grouping();
        bestCost = nests.getFirst().total();
      }
      // the host birds find the worst eggs and build new nests
      int abandoned = (int) Math.round(settings.abandon() * nests.size());
      for (int nest = nests.size() - abandoned; nest < nests.size(); nest++) {
        nests.set(nest, new TrackedCost(problem, greedy.solve(problem, budget, random)));
      }
    }
    return best != null ? best : nests.getFirst().grouping();
  }

  // a Levy flight: as many random steps as a Levy-distributed length, every step a cost calculation
  private void fly(MatchingProblem problem, TrackedCost egg, MatchingBudget budget, Random random) {
    int steps = 1 + (int) Math.min(problem.size(), Math.abs(levy(random)) * settings.flightScale());
    for (int step = 0; step < steps && budget.spend(); step++) {
      Step.applyRandom(problem, egg, random);
    }
  }

  // Mantegna: u / |v|^(1/beta), with u ~ N(0, sigma^2) and v ~ N(0, 1)
  private static double levy(Random random) {
    return random.nextGaussian() * SIGMA / Math.pow(Math.abs(random.nextGaussian()), 1 / BETA);
  }

}
