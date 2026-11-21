package com.studymatch.matching;

import java.util.Random;

// Firefly Algorithm (Yang) on random keys: every firefly flies towards every brighter one, pulled less the further
// away it is, plus a small random wander that fades as the budget runs out
public final class FireflyMatching implements MatchingAlgorithm {

  // fireflies: swarm size; attraction: pull at distance 0; absorption: how fast the pull fades with distance;
  // wander: size of the random step at the start
  public record Settings(int fireflies, double attraction, double absorption, double wander) {
  }

  // from MatchingTuning
  public static final Settings DEFAULTS = new Settings(15, 1, 0.1, 0.1);

  private final GreedyMatching greedy = new GreedyMatching();
  private final Settings settings;

  public FireflyMatching() {
    this(DEFAULTS);
  }

  public FireflyMatching(Settings settings) {
    this.settings = settings;
  }

  @Override
  public Grouping solve(MatchingProblem problem, MatchingBudget budget, Random random) {
    double[][] flies = new double[settings.fireflies()][];
    double[] costs = new double[settings.fireflies()];
    int brightest = -1;
    for (int fly = 0; fly < flies.length && budget.spend(); fly++) {
      flies[fly] = RandomKeys.random(problem.size(), random);
      costs[fly] = RandomKeys.score(problem, flies[fly]);
      brightest = brightest < 0 || costs[fly] < costs[brightest] ? fly : brightest;
    }
    if (brightest < 0) {
      return greedy.solve(problem, budget, random);
    }
    double[] best = flies[brightest].clone();
    double bestCost = costs[brightest];

    while (!budget.isExhausted()) {
      double wander = settings.wander() * (1 - budget.progress());
      for (int fly = 0; fly < flies.length && flies[fly] != null && !budget.isExhausted(); fly++) {
        boolean moved = false;
        for (int other = 0; other < flies.length && flies[other] != null; other++) {
          if (costs[other] >= costs[fly]) {
            continue;
          }
          approach(flies[fly], flies[other], wander, random);
          moved = true;
          if (!evaluate(problem, flies, costs, fly, budget)) {
            break;
          }
          if (costs[fly] < bestCost) {
            best = flies[fly].clone();
            bestCost = costs[fly];
          }
        }
        // the brightest (or one with no brighter neighbour) wanders on its own, as in the paper
        if (!moved) {
          approach(flies[fly], flies[fly], Math.max(wander, settings.wander() * 0.01), random);
          evaluate(problem, flies, costs, fly, budget);
        }
        if (costs[fly] < bestCost) {
          best = flies[fly].clone();
          bestCost = costs[fly];
        }
      }
    }
    Grouping result = RandomKeys.finish(problem, best, random);
    return result != null ? result : greedy.solve(problem, budget, random);
  }

  private static boolean evaluate(MatchingProblem problem, double[][] flies, double[] costs, int fly,
      MatchingBudget budget) {
    if (!budget.spend()) {
      return false;
    }
    costs[fly] = RandomKeys.score(problem, flies[fly]);
    return true;
  }

  private void approach(double[] fly, double[] brighter, double wander, Random random) {
    double squared = 0;
    for (int student = 0; student < fly.length; student++) {
      squared += (fly[student] - brighter[student]) * (fly[student] - brighter[student]);
    }
    double pull = settings.attraction() * Math.exp(-settings.absorption() * squared);
    for (int student = 0; student < fly.length; student++) {
      fly[student] += pull * (brighter[student] - fly[student]) + wander * (random.nextDouble() - 0.5);
      fly[student] = Math.clamp(fly[student], 0, 1);
    }
  }

}
