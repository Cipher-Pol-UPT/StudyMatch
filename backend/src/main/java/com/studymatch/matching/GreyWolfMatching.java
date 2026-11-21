package com.studymatch.matching;

import java.util.Random;

// Grey Wolf Optimizer (Mirjalili) on random keys: the three best wolves lead and every wolf moves to the average of
// three positions around them; the circle closes as the budget runs out (a from 2 to 0)
public final class GreyWolfMatching implements MatchingAlgorithm {

  public record Settings(int wolves) {
  }

  // from MatchingTuning
  public static final Settings DEFAULTS = new Settings(40);

  private final GreedyMatching greedy = new GreedyMatching();
  private final Settings settings;

  public GreyWolfMatching() {
    this(DEFAULTS);
  }

  public GreyWolfMatching(Settings settings) {
    this.settings = settings;
  }

  @Override
  public Grouping solve(MatchingProblem problem, MatchingBudget budget, Random random) {
    double[][] wolves = new double[settings.wolves()][];
    double[] costs = new double[settings.wolves()];
    for (int wolf = 0; wolf < wolves.length && budget.spend(); wolf++) {
      wolves[wolf] = RandomKeys.random(problem.size(), random);
      costs[wolf] = RandomKeys.score(problem, wolves[wolf]);
    }
    if (wolves[0] == null) {
      return greedy.solve(problem, budget, random);
    }
    // alpha, beta and delta: the three best positions so far, with their costs
    double[][] leaders = new double[3][];
    double[] leaderCosts = { Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY };
    for (int wolf = 0; wolf < wolves.length && wolves[wolf] != null; wolf++) {
      rank(leaders, leaderCosts, wolves[wolf], costs[wolf]);
    }

    while (!budget.isExhausted()) {
      double a = 2 * (1 - budget.progress());
      for (int wolf = 0; wolf < wolves.length && wolves[wolf] != null; wolf++) {
        hunt(wolves[wolf], leaders, a, random);
        if (!budget.spend()) {
          break;
        }
        costs[wolf] = RandomKeys.score(problem, wolves[wolf]);
        rank(leaders, leaderCosts, wolves[wolf], costs[wolf]);
      }
    }
    Grouping result = RandomKeys.finish(problem, leaders[0], random);
    return result != null ? result : greedy.solve(problem, budget, random);
  }

  private static void hunt(double[] wolf, double[][] leaders, double a, Random random) {
    for (int student = 0; student < wolf.length; student++) {
      double sum = 0;
      for (double[] leader : leaders) {
        if (leader == null) {
          sum += wolf[student];
          continue;
        }
        double coefficient = 2 * a * random.nextDouble() - a;
        double distance = Math.abs(2 * random.nextDouble() * leader[student] - wolf[student]);
        sum += leader[student] - coefficient * distance;
      }
      wolf[student] = Math.clamp(sum / leaders.length, 0, 1);
    }
  }

  // keeps the three best positions in order
  private static void rank(double[][] leaders, double[] leaderCosts, double[] wolf, double cost) {
    for (int place = 0; place < 3; place++) {
      if (cost < leaderCosts[place]) {
        for (int lower = 2; lower > place; lower--) {
          leaders[lower] = leaders[lower - 1];
          leaderCosts[lower] = leaderCosts[lower - 1];
        }
        leaders[place] = wolf.clone();
        leaderCosts[place] = cost;
        return;
      }
    }
  }

}
