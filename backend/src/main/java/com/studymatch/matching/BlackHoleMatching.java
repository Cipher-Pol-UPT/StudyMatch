package com.studymatch.matching;

import java.util.Random;

// Black Hole (Hatamlou) on random keys: the best star is the black hole and every star drifts towards it; a star
// that gets better takes its place, one that crosses the event horizon is swallowed and born again somewhere random
public final class BlackHoleMatching implements MatchingAlgorithm {

  public record Settings(int stars) {
  }

  // from MatchingTuning
  public static final Settings DEFAULTS = new Settings(10);

  private final GreedyMatching greedy = new GreedyMatching();
  private final Settings settings;

  public BlackHoleMatching() {
    this(DEFAULTS);
  }

  public BlackHoleMatching(Settings settings) {
    this.settings = settings;
  }

  @Override
  public Grouping solve(MatchingProblem problem, MatchingBudget budget, Random random) {
    double[][] stars = new double[settings.stars()][];
    double[] costs = new double[settings.stars()];
    int hole = -1;
    for (int star = 0; star < stars.length && budget.spend(); star++) {
      stars[star] = RandomKeys.random(problem.size(), random);
      costs[star] = RandomKeys.score(problem, stars[star]);
      hole = hole < 0 || costs[star] < costs[hole] ? star : hole;
    }
    if (hole < 0) {
      return greedy.solve(problem, budget, random);
    }

    while (!budget.isExhausted()) {
      double total = 0;
      for (int star = 0; star < stars.length && stars[star] != null; star++) {
        if (star == hole) {
          continue;
        }
        drift(stars[star], stars[hole], random);
        if (!budget.spend()) {
          break;
        }
        costs[star] = RandomKeys.score(problem, stars[star]);
        if (costs[star] < costs[hole]) {
          hole = star;
        }
        total += costs[star];
      }
      // the event horizon: the black hole's share of all the cost
      double horizon = costs[hole] / Math.max(total + costs[hole], Double.MIN_VALUE);
      for (int star = 0; star < stars.length && stars[star] != null; star++) {
        if (star != hole && distance(stars[star], stars[hole]) < horizon && budget.spend()) {
          stars[star] = RandomKeys.random(problem.size(), random);
          costs[star] = RandomKeys.score(problem, stars[star]);
        }
      }
    }
    Grouping result = RandomKeys.finish(problem, stars[hole], random);
    return result != null ? result : greedy.solve(problem, budget, random);
  }

  private static void drift(double[] star, double[] hole, Random random) {
    for (int student = 0; student < star.length; student++) {
      star[student] += random.nextDouble() * (hole[student] - star[student]);
    }
  }

  private static double distance(double[] first, double[] second) {
    double sum = 0;
    for (int student = 0; student < first.length; student++) {
      sum += (first[student] - second[student]) * (first[student] - second[student]);
    }
    return Math.sqrt(sum);
  }

}
