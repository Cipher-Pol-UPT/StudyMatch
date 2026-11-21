package com.studymatch.matching;

import java.util.Arrays;
import java.util.Random;

// Late Acceptance Hill Climbing (Burke & Bykov): a step is kept if it's no worse than now or than the cost
// historyLength steps ago, so it can climb out of dead ends without a temperature to tune
public final class LateAcceptanceMatching implements MatchingAlgorithm {

  public record Settings(int historyLength) {
  }

  // from MatchingTuning: 5000 best, 20000 was far worse
  public static final Settings DEFAULTS = new Settings(5_000);

  // steps that break a rule aren't evaluated, this stops the loop if nearly all of them do
  static final int MAX_FAILED_TRIES = 10_000;

  private final GreedyMatching greedy = new GreedyMatching();
  private final Settings settings;

  public LateAcceptanceMatching() {
    this(DEFAULTS);
  }

  public LateAcceptanceMatching(Settings settings) {
    this.settings = settings;
  }

  @Override
  public Grouping solve(MatchingProblem problem, MatchingBudget budget, Random random) {
    Grouping start = greedy.solve(problem, budget, random);
    if (problem.getGroupCount() < 2) {
      return start;
    }
    TrackedCost current = new TrackedCost(problem, start);
    double cost = current.total();
    Grouping best = start;
    double bestCost = cost;
    double[] history = new double[settings.historyLength()];
    Arrays.fill(history, cost);
    long iteration = 0;
    int failedTries = 0;

    while (!budget.isExhausted()) {
      Step step = Step.random(problem, current, random);
      if (!step.allowed(problem, current)) {
        if (++failedTries > MAX_FAILED_TRIES) {
          break;
        }
        continue;
      }
      failedTries = 0;
      if (!budget.spend()) {
        break;
      }
      double changed = step.apply(current);
      int slot = (int) (iteration++ % history.length);
      if (changed <= cost || changed <= history[slot]) {
        cost = changed;
        if (cost < bestCost) {
          best = current.grouping();
          bestCost = cost;
        }
      } else {
        step.undo(current);
      }
      history[slot] = cost;
    }
    return best;
  }

}
