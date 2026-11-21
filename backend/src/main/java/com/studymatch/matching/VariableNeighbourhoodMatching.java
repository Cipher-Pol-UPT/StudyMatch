package com.studymatch.matching;

import java.util.Random;

// Variable Neighbourhood Search (Mladenovic & Hansen): shake with k random steps and polish; a better result
// moves there and k starts again at 1, otherwise the next shake is one step bigger, up to maxShake
public final class VariableNeighbourhoodMatching implements MatchingAlgorithm {

  // maxShake: largest shake; polishSteps: cost calculations per polish
  public record Settings(int maxShake, int polishSteps) {
  }

  // from MatchingTuning: a long polish mattered most
  public static final Settings DEFAULTS = new Settings(8, 5_000);

  private final GreedyMatching greedy = new GreedyMatching();
  private final Settings settings;
  private final LocalSearch localSearch;

  public VariableNeighbourhoodMatching() {
    this(DEFAULTS);
  }

  public VariableNeighbourhoodMatching(Settings settings) {
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
    int shake = 1;
    while (!budget.isExhausted()) {
      TrackedCost shaken = Kick.apply(problem, current, shake, budget, random);
      TrackedCost polished = localSearch.polish(problem, shaken.grouping(), random, budget::spend).polished();
      if (polished.total() < current.total()) {
        current = polished;
        shake = 1;
      } else {
        shake = shake % settings.maxShake() + 1;
      }
    }
    return current.grouping();
  }

}
