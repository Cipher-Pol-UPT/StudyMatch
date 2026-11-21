package com.studymatch.matching;

import java.util.Random;

// tabu search on its own: from a greedy grouping until it stops improving, then again from a new greedy one
public final class TabuSearchMatching implements MatchingAlgorithm {

  // tenure: steps a swapped student can't move again; sample: swaps looked at per step;
  // targetedShare: swaps that start from a group paying a penalty; patience: steps without a new best before a restart
  public record Settings(int tenure, int sample, double targetedShare, int patience) {
  }

  // from MatchingTuning: a sample of 20 wanders uphill, 1000 swaps per step makes the forced moves good ones
  public static final Settings DEFAULTS = new Settings(5, 1_000, 0.25, 200);

  private final GreedyMatching greedy = new GreedyMatching();
  private final Settings settings;

  public TabuSearchMatching() {
    this(DEFAULTS);
  }

  public TabuSearchMatching(Settings settings) {
    this.settings = settings;
  }

  @Override
  public Grouping solve(MatchingProblem problem, MatchingBudget budget, Random random) {
    Grouping start = greedy.solve(problem, budget, random);
    Grouping best = start;
    double bestCost = MatchingCost.cost(problem, start);

    while (!budget.isExhausted()) {
      TabuSearch.Outcome outcome = TabuSearch.search(problem, start, Integer.MAX_VALUE, settings.patience(),
          settings.tenure(), settings.sample(), settings.targetedShare(), random, budget::spend);
      if (outcome.best().total() < bestCost) {
        best = outcome.best().grouping();
        bestCost = outcome.best().total();
      }
      // no allowed swap at all (one group, or everyone separated) would restart forever without using the budget
      if (outcome.outOfBudget() || outcome.evaluations() == 0) {
        break;
      }
      // greedy breaks ties at random, so every restart starts somewhere else
      start = greedy.solve(problem, budget, random);
    }
    return best;
  }

}
