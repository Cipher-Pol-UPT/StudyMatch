package com.studymatch.matching;

import java.util.Random;

// a copy of a grouping a few random steps away, every step a cost calculation
final class Kick {

  private Kick() {
  }

  static TrackedCost apply(MatchingProblem problem, TrackedCost from, int steps, MatchingBudget budget,
      Random random) {
    TrackedCost copy = new TrackedCost(problem, from.grouping());
    for (int step = 0; step < steps && budget.spend(); step++) {
      Step.applyRandom(problem, copy, random);
    }
    return copy;
  }

}
