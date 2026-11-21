package com.studymatch.matching;

import java.util.Comparator;
import java.util.Random;
import java.util.stream.IntStream;

// for the algorithms that move vectors of numbers (PSO, black hole): a number per student, sorted, fills the groups
final class RandomKeys {

  // the filling doesn't know about separations, so breaking one costs this much
  static final double SEPARATION_PENALTY = 100;

  private RandomKeys() {
  }

  static double[] random(int students, Random random) {
    double[] keys = new double[students];
    for (int student = 0; student < students; student++) {
      keys[student] = random.nextDouble();
    }
    return keys;
  }

  static Grouping decode(MatchingProblem problem, double[] keys) {
    int[] order = IntStream.range(0, keys.length).boxed()
        .sorted(Comparator.comparingDouble(student -> keys[student])).mapToInt(Integer::intValue).toArray();
    return new Grouping(problem.fill(order), problem.getGroupCount());
  }

  static double score(MatchingProblem problem, double[] keys) {
    Grouping grouping = decode(problem, keys);
    return MatchingCost.cost(problem, grouping) + SEPARATION_PENALTY * SeparationRepair.broken(problem, grouping);
  }

  // the grouping of the best keys, with separations fixed; null if they can't be
  static Grouping finish(MatchingProblem problem, double[] keys, Random random) {
    Grouping grouping = decode(problem, keys);
    SeparationRepair.repair(problem, grouping, random);
    return MatchingCost.isValid(problem, grouping) ? grouping : null;
  }

}
