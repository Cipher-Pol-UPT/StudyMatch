package com.studymatch.matching;

import java.util.Random;

// Whale Optimization (Mirjalili & Lewis) on random keys: each whale encircles the best one (or a random one while
// still exploring) or swims a bubble-net spiral towards it; a goes from 2 to 0 as the budget runs out
public final class WhaleMatching implements MatchingAlgorithm {

  public record Settings(int whales) {
  }

  // from MatchingTuning
  public static final Settings DEFAULTS = new Settings(80);

  // shape of the logarithmic spiral, 1 in the paper
  private static final double SPIRAL = 1;

  private final GreedyMatching greedy = new GreedyMatching();
  private final Settings settings;

  public WhaleMatching() {
    this(DEFAULTS);
  }

  public WhaleMatching(Settings settings) {
    this.settings = settings;
  }

  @Override
  public Grouping solve(MatchingProblem problem, MatchingBudget budget, Random random) {
    double[][] whales = new double[settings.whales()][];
    double[] best = null;
    double bestCost = Double.POSITIVE_INFINITY;
    int count = 0;
    for (; count < whales.length && budget.spend(); count++) {
      whales[count] = RandomKeys.random(problem.size(), random);
      double cost = RandomKeys.score(problem, whales[count]);
      if (cost < bestCost) {
        best = whales[count].clone();
        bestCost = cost;
      }
    }
    if (best == null) {
      return greedy.solve(problem, budget, random);
    }

    while (!budget.isExhausted()) {
      double a = 2 * (1 - budget.progress());
      for (int whale = 0; whale < count; whale++) {
        swim(whales[whale], best, whales[random.nextInt(count)], a, random);
        if (!budget.spend()) {
          break;
        }
        double cost = RandomKeys.score(problem, whales[whale]);
        if (cost < bestCost) {
          best = whales[whale].clone();
          bestCost = cost;
        }
      }
    }
    Grouping result = RandomKeys.finish(problem, best, random);
    return result != null ? result : greedy.solve(problem, budget, random);
  }

  private static void swim(double[] whale, double[] best, double[] other, double a, Random random) {
    double coefficient = 2 * a * random.nextDouble() - a;
    double reach = 2 * random.nextDouble();
    boolean spiral = random.nextBoolean();
    double l = 2 * random.nextDouble() - 1;
    // while |A| is large the whales still explore around a random whale instead of the best
    double[] target = Math.abs(coefficient) < 1 ? best : other;
    for (int student = 0; student < whale.length; student++) {
      double next;
      if (spiral) {
        next = Math.abs(best[student] - whale[student]) * Math.exp(SPIRAL * l) * Math.cos(2 * Math.PI * l)
            + best[student];
      } else {
        next = target[student] - coefficient * Math.abs(reach * target[student] - whale[student]);
      }
      whale[student] = Math.clamp(next, 0, 1);
    }
  }

}
