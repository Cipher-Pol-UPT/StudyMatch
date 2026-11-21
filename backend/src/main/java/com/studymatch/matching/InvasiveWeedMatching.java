package com.studymatch.matching;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

// Invasive Weed Optimization (Mehrabian & Lucas): better weeds spread more seeds, seeds land far at first and close
// to home at the end, and once the field is full only the best weeds survive
public final class InvasiveWeedMatching implements MatchingAlgorithm {

  // weeds: starting field; maxPopulation: field size after exclusion; minSeeds, maxSeeds: seeds of the worst and
  // the best weed; initialSpread: random steps a seed is from home at the start (1 at the end)
  public record Settings(int weeds, int maxPopulation, int minSeeds, int maxSeeds, double initialSpread) {
  }

  // from MatchingTuning: many seeds that land close to home
  public static final Settings DEFAULTS = new Settings(10, 20, 1, 8, 4);

  // how fast the spread shrinks over the budget, the value from the paper
  private static final double SPREAD_EXPONENT = 3;

  private final GreedyMatching greedy = new GreedyMatching();
  private final Settings settings;

  public InvasiveWeedMatching() {
    this(DEFAULTS);
  }

  public InvasiveWeedMatching(Settings settings) {
    this.settings = settings;
  }

  @Override
  public Grouping solve(MatchingProblem problem, MatchingBudget budget, Random random) {
    List<TrackedCost> field = new ArrayList<>();
    for (int weed = 0; weed < settings.weeds(); weed++) {
      field.add(new TrackedCost(problem, greedy.solve(problem, budget, random)));
    }
    if (problem.getGroupCount() < 2) {
      return field.getFirst().grouping();
    }
    Comparator<TrackedCost> byCost = Comparator.comparingDouble(TrackedCost::total);

    while (!budget.isExhausted()) {
      field.sort(byCost);
      double best = field.getFirst().total();
      double worst = field.getLast().total();
      double spread = Math.pow(1 - budget.progress(), SPREAD_EXPONENT) * (settings.initialSpread() - 1) + 1;
      List<TrackedCost> seeds = new ArrayList<>();
      for (TrackedCost weed : field) {
        double quality = worst > best ? (worst - weed.total()) / (worst - best) : 1;
        int count = settings.minSeeds() + (int) Math.round(quality * (settings.maxSeeds() - settings.minSeeds()));
        for (int seed = 0; seed < count && !budget.isExhausted(); seed++) {
          seeds.add(disperse(problem, weed, spread, budget, random));
        }
      }
      field.addAll(seeds);
      field.sort(byCost);
      // competitive exclusion
      while (field.size() > settings.maxPopulation()) {
        field.removeLast();
      }
    }
    field.sort(byCost);
    return field.getFirst().grouping();
  }

  // a seed: a copy of the weed some random steps away, fewer as the budget runs out
  private static TrackedCost disperse(MatchingProblem problem, TrackedCost weed, double spread, MatchingBudget budget,
      Random random) {
    TrackedCost seed = new TrackedCost(problem, weed.grouping());
    int steps = 1 + (int) Math.abs(random.nextGaussian() * spread);
    for (int step = 0; step < steps && budget.spend(); step++) {
      Step.applyRandom(problem, seed, random);
    }
    return seed;
  }

}
