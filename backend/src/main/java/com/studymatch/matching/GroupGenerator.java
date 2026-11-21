package com.studymatch.matching;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import com.studymatch.matching.MatchingProblem.Member;

// one assignment's groups: ranks the edition, splits it into pools and runs the algorithm on each pool
public final class GroupGenerator {

  private final MatchingAlgorithm algorithm;
  private final Duration timeLimit;

  public GroupGenerator(MatchingAlgorithm algorithm, Duration timeLimit) {
    this.algorithm = algorithm;
    this.timeLimit = timeLimit;
  }

  // the groups as positions in the candidate list; separations are pairs of positions too
  public List<List<Integer>> generate(List<Candidate> candidates, List<int[]> separations, int minSize,
      int optimalSize, int maxSize, boolean sameClass, Random random) {
    List<Member> members = EditionScores.members(candidates);
    List<List<Integer>> groups = new ArrayList<>();
    for (List<Integer> pool : PoolPlanner.plan(candidates, minSize, optimalSize, maxSize, sameClass)) {
      MatchingProblem problem = new MatchingProblem(pool.stream().map(members::get).toList(),
          separationsWithin(pool, separations), minSize, optimalSize, maxSize);
      Grouping grouping = algorithm.solve(problem, MatchingBudget.time(share(pool, candidates)), random);
      for (int group = 0; group < grouping.groupCount(); group++) {
        groups.add(grouping.members(group).stream().map(pool::get).toList());
      }
    }
    return groups;
  }

  // what generate will do, estimated before running it from the size of each class it keeps apart (one size when
  // classes mix), so nobody's evidence is loaded. Generate can split a class further by attendance tier, so the
  // groups can be off by one and the search is the most it takes: a tier of one group searches nothing.
  // No groups and no search when a class can't be split; generating says why
  public Plan estimate(List<Integer> classSizes, int minSize, int optimalSize, int maxSize) {
    int students = classSizes.stream().mapToInt(Integer::intValue).sum();
    int groups = 0;
    Duration search = Duration.ZERO;
    for (int size : classSizes) {
      int count = MatchingProblem.groupCount(size, minSize, optimalSize, maxSize);
      if (count < 1) {
        return new Plan(0, Duration.ZERO);
      }
      groups += count;
      // a class of one group has nothing to search, the algorithm hands it back straight away
      if (count >= 2) {
        search = search.plus(timeLimit.multipliedBy(size).dividedBy(students));
      }
    }
    return new Plan(groups, search);
  }

  public record Plan(int groups, Duration search) {
  }

  // bigger pools get more of the time
  private Duration share(List<Integer> pool, List<Candidate> candidates) {
    return timeLimit.multipliedBy(pool.size()).dividedBy(candidates.size());
  }

  // the pairs with both students in the pool, as positions in the pool
  private static List<int[]> separationsWithin(List<Integer> pool, List<int[]> separations) {
    Map<Integer, Integer> position = new HashMap<>();
    for (int i = 0; i < pool.size(); i++) {
      position.put(pool.get(i), i);
    }
    return separations.stream()
        .filter(pair -> position.containsKey(pair[0]) && position.containsKey(pair[1]))
        .map(pair -> new int[] { position.get(pair[0]), position.get(pair[1]) })
        .toList();
  }

}
