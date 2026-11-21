package com.studymatch.matching;

import java.util.Random;

// Iterated Local Search (Lourenco, Martin & Stutzle): polish, kick with a few random steps, polish again, keep it
// if it's better
public final class IteratedLocalSearchMatching implements MatchingAlgorithm {

  // kick: random steps between polishes; polishSteps: cost calculations per polish
  public record Settings(int kick, int polishSteps) {
  }

  // from MatchingTuning: small kicks were far better than big ones
  public static final Settings DEFAULTS = new Settings(2, 5_000);

  private final GreedyMatching greedy = new GreedyMatching();
  private final Settings settings;
  private final LocalSearch localSearch;

  public IteratedLocalSearchMatching() {
    this(DEFAULTS);
  }

  public IteratedLocalSearchMatching(Settings settings) {
    this.settings = settings;
    this.localSearch = new LocalSearch(new LocalSearch.Settings(settings.polishSteps(), 0, 0.5, 0, 0, 0, 20));
  }

  @Override
  public Grouping solve(MatchingProblem problem, MatchingBudget budget, Random random) {
    Grouping start = greedy.solve(problem, budget, random);
    if (problem.getGroupCount() < 2) {
      return start;
    }
    TrackedCost current = localSearch.polish(problem, start, random, budget::spend).polished();
    while (!budget.isExhausted()) {
      TrackedCost kicked = Kick.apply(problem, current, settings.kick(), budget, random);
      TrackedCost polished = localSearch.polish(problem, kicked.grouping(), random, budget::spend).polished();
      if (polished.total() < current.total()) {
        current = polished;
      }
    }
    return current.grouping();
  }

}
