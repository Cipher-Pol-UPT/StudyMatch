package com.studymatch.matching;

import java.util.Comparator;
import java.util.Random;
import java.util.stream.IntStream;

// for the algorithms that move vectors of numbers (FTTA): a number per student, sorted, fills the groups
final class RandomKeys {

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
    return SeparationRepair.penalizedCost(problem, decode(problem, keys));
  }

  // the grouping of the best keys, with separations fixed; null if they can't be
  static Grouping finish(MatchingProblem problem, double[] keys, Random random) {
    Grouping grouping = decode(problem, keys);
    SeparationRepair.repair(problem, grouping, random);
    return MatchingCost.isValid(problem, grouping) ? grouping : null;
  }

}
