package com.studymatch.matching;

import static com.studymatch.matching.MatchingCost.canJoin;

import java.util.Random;

// for the algorithms that fill groups by order (FTTA, genetic): the filling doesn't know about separations
final class SeparationRepair {

  private SeparationRepair() {
  }

  static long broken(MatchingProblem problem, Grouping grouping) {
    return problem.getSeparations().stream()
        .filter(pair -> grouping.groupOf(pair[0]) == grouping.groupOf(pair[1])).count();
  }

  // swaps one of each separated pair out of the group, if somewhere takes them without breaking another
  static void repair(MatchingProblem problem, Grouping grouping, Random random) {
    for (int round = 0; round < problem.getSeparations().size(); round++) {
      int[] broken = problem.getSeparations().stream()
          .filter(pair -> grouping.groupOf(pair[0]) == grouping.groupOf(pair[1]))
          .findFirst().orElse(null);
      if (broken == null) {
        return;
      }
      int student = broken[0];
      int from = grouping.groupOf(student);
      int start = random.nextInt(problem.size());
      boolean fixed = false;
      for (int i = 0; i < problem.size() && !fixed; i++) {
        int other = (start + i) % problem.size();
        int to = grouping.groupOf(other);
        if (to != from && canJoin(problem, grouping, student, to, other)
            && canJoin(problem, grouping, other, from, student)) {
          grouping.swap(student, other);
          fixed = true;
        }
      }
      if (!fixed) {
        return;
      }
    }
  }

}
