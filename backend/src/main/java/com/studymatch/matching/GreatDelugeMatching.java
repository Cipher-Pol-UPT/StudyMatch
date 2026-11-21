package com.studymatch.matching;

import java.util.Random;

// Great Deluge (Dueck): a step is kept if it isn't worse or if it stays under the water level, which starts at the
// first cost and falls steadily as the budget runs out
public final class GreatDelugeMatching implements MatchingAlgorithm {

  // the level starts at start times the first cost and ends at end times it
  public record Settings(double start, double end) {
  }

  public static final Settings DEFAULTS = new Settings(1, 0);

  // steps that break a rule aren't evaluated, this stops the loop if nearly all of them do
  static final int MAX_FAILED_TRIES = 10_000;

  private final GreedyMatching greedy = new GreedyMatching();
  private final Settings settings;

  public GreatDelugeMatching() {
    this(DEFAULTS);
  }

  public GreatDelugeMatching(Settings settings) {
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
    double firstCost = cost;
    Grouping best = start;
    double bestCost = cost;
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
      double level = firstCost * (settings.start() + (settings.end() - settings.start()) * budget.progress());
      double changed = step.apply(current);
      if (changed <= cost || changed <= level) {
        cost = changed;
        if (cost < bestCost) {
          best = current.grouping();
          bestCost = cost;
        }
      } else {
        step.undo(current);
      }
    }
    return best;
  }

}
