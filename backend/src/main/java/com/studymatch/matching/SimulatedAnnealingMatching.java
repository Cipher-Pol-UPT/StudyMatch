package com.studymatch.matching;

import java.util.Random;

// start from greedy, try random swaps and moves, and sometimes keep a worse one so it doesn't get stuck
public final class SimulatedAnnealingMatching implements MatchingAlgorithm {

  // hot at the start (accepts almost anything), cold at the end (only improvements)
  public record Settings(double startTemperature, double endTemperature) {
  }

  public static final Settings DEFAULTS = new Settings(1.0, 0.001);

  // moves that break a rule aren't evaluated, this stops the loop if nearly all of them do
  static final int MAX_FAILED_TRIES = 10_000;

  private final GreedyMatching greedy = new GreedyMatching();
  private final Settings settings;

  public SimulatedAnnealingMatching() {
    this(DEFAULTS);
  }

  public SimulatedAnnealingMatching(Settings settings) {
    this.settings = settings;
  }

  @Override
  public Grouping solve(MatchingProblem problem, MatchingBudget budget, Random random) {
    Grouping start = greedy.solve(problem, budget, random);
    if (problem.getGroupCount() < 2) {
      return start;
    }
    TrackedCost current = new TrackedCost(problem, start);
    double currentCost = current.total();
    Grouping best = start;
    double bestCost = currentCost;
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

      double cost = step.apply(current);
      double increase = cost - currentCost;
      if (increase <= 0 || random.nextDouble() < Math.exp(-increase / temperature(budget))) {
        currentCost = cost;
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

  // cools geometrically from the start to the end temperature as the budget runs out
  private double temperature(MatchingBudget budget) {
    double start = settings.startTemperature();
    return start * Math.pow(settings.endTemperature() / start, budget.progress());
  }

}
