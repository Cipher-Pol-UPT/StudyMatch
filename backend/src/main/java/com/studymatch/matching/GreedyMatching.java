package com.studymatch.matching;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.studymatch.matching.MatchingProblem.Member;

// the baseline: place the students one by one where they add the least cost, never going back
public final class GreedyMatching implements MatchingAlgorithm {

  // new random orders to try before deciding the separations can't be met; with tight separations an order can
  // hit a dead end now and then, and the algorithms that restart from greedy call it hundreds of times per run
  static final int MAX_ATTEMPTS = 100;

  // one pass needs far fewer evaluations than any budget, so the budget isn't used
  @Override
  public Grouping solve(MatchingProblem problem, MatchingBudget budget, Random random) {
    for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
      int[] groupOf = place(problem, random);
      if (groupOf != null) {
        return new Grouping(groupOf, problem.getGroupCount());
      }
    }
    throw new UnmetSeparationsException();
  }

  // null when a student ends up with nowhere to go
  private static int[] place(MatchingProblem problem, Random random) {
    int groups = problem.getGroupCount();
    int[] capacity = problem.groupSizes();
    List<List<Integer>> members = new ArrayList<>();
    for (int group = 0; group < groups; group++) {
      members.add(new ArrayList<>());
    }
    int[] groupOf = new int[problem.size()];

    for (int student : problem.placingOrder(random)) {
      int best = -1;
      double bestIncrease = Double.POSITIVE_INFINITY;
      int start = random.nextInt(groups);
      for (int i = 0; i < groups; i++) {
        int group = (start + i) % groups;
        List<Integer> current = members.get(group);
        if (current.size() >= capacity[group]
            || current.stream().anyMatch(member -> problem.separated(student, member))) {
          continue;
        }
        List<Integer> joined = new ArrayList<>(current);
        joined.add(student);
        double increase = groupCost(problem, joined) - groupCost(problem, current);
        if (increase < bestIncrease) {
          best = group;
          bestIncrease = increase;
        }
      }
      if (best == -1) {
        return null;
      }
      members.get(best).add(student);
      groupOf[student] = best;
    }
    return groupOf;
  }

  private static double groupCost(MatchingProblem problem, List<Integer> students) {
    List<Member> members = students.stream().map(problem::member).toList();
    return MatchingCost.groupCost(problem, members);
  }

}
