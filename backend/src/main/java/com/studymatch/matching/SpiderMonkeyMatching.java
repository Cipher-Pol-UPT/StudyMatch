package com.studymatch.matching;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

// Spider Monkey Optimization (Bansal et al.), from their fission-fusion society: monkeys learn from their group's
// leader and from the global leader; a stuck local leader sends its group exploring, a stuck global leader splits
// the troop into more groups, and once there are too many they merge back into one
public final class SpiderMonkeyMatching implements MatchingAlgorithm {

  // monkeys: troop size; maxGroups: most groups after fissions; localLimit, globalLimit: rounds a leader may go
  // without improving; perturbation: chance a monkey in a stuck group starts over instead of following the leader
  public record Settings(int monkeys, int maxGroups, int localLimit, int globalLimit, double perturbation) {
  }

  // from MatchingTuning: patient local leaders, impatient global leader; on 80 students a smaller troop
  public static final Settings DEFAULTS = new Settings(20, 4, 200, 20, 0.3);

  private final GreedyMatching greedy = new GreedyMatching();
  private final Settings settings;

  public SpiderMonkeyMatching() {
    this(DEFAULTS);
  }

  public SpiderMonkeyMatching(Settings settings) {
    this.settings = settings;
  }

  @Override
  public Grouping solve(MatchingProblem problem, MatchingBudget budget, Random random) {
    List<TrackedCost> troop = new ArrayList<>();
    for (int monkey = 0; monkey < settings.monkeys(); monkey++) {
      troop.add(new TrackedCost(problem, greedy.solve(problem, budget, random)));
    }
    if (problem.getGroupCount() < 2) {
      return troop.getFirst().grouping();
    }
    int groups = 1;
    int[] localStuck = new int[settings.maxGroups()];
    double[] localBest = new double[settings.maxGroups()];
    Arrays.fill(localBest, Double.POSITIVE_INFINITY);
    TrackedCost global = best(troop, 0, troop.size());
    double globalBest = global.total();
    int globalStuck = 0;

    while (!budget.isExhausted()) {
      for (int group = 0; group < groups; group++) {
        int from = group * troop.size() / groups;
        int to = (group + 1) * troop.size() / groups;
        TrackedCost leader = best(troop, from, to);
        for (int monkey = from; monkey < to && !budget.isExhausted(); monkey++) {
          follow(problem, troop, monkey, leader, troop.get(from + random.nextInt(to - from)), budget, random);
          // the global leader phase: better monkeys are likelier to learn from it
          if (random.nextDouble() < quality(troop.get(monkey), global)) {
            follow(problem, troop, monkey, global, troop.get(random.nextInt(troop.size())), budget, random);
          }
        }
        TrackedCost newLeader = best(troop, from, to);
        localStuck[group] = newLeader.total() < localBest[group] ? 0 : localStuck[group] + 1;
        localBest[group] = Math.min(localBest[group], newLeader.total());
        if (localStuck[group] > settings.localLimit()) {
          scatter(problem, troop, from, to, global, budget, random);
          localStuck[group] = 0;
        }
      }
      global = best(troop, 0, troop.size());
      globalStuck = global.total() < globalBest ? 0 : globalStuck + 1;
      globalBest = Math.min(globalBest, global.total());
      if (globalStuck > settings.globalLimit()) {
        // fission into one more group, or fusion back into one
        groups = groups < settings.maxGroups() ? groups + 1 : 1;
        Arrays.fill(localStuck, 0);
        Arrays.fill(localBest, Double.POSITIVE_INFINITY);
        globalStuck = 0;
      }
    }
    return best(troop, 0, troop.size()).grouping();
  }

  // learns a pairing from the leader and one from another monkey, in place; undone if it isn't better
  private static void follow(MatchingProblem problem, List<TrackedCost> troop, int monkey, TrackedCost leader,
      TrackedCost other, MatchingBudget budget, Random random) {
    TrackedCost current = troop.get(monkey);
    double before = current.total();
    List<Step> taken = new ArrayList<>(2);
    for (TrackedCost teacher : List.of(leader, other)) {
      Step step = Step.learnPair(problem, current, teacher, random);
      if (step != null && step.allowed(problem, current) && budget.spend()) {
        step.apply(current);
        taken.add(step);
      }
    }
    if (current.total() >= before) {
      for (int index = taken.size() - 1; index >= 0; index--) {
        taken.get(index).undo(current);
      }
    }
  }

  // a stuck group: each monkey starts over or follows the global leader
  private void scatter(MatchingProblem problem, List<TrackedCost> troop, int from, int to, TrackedCost global,
      MatchingBudget budget, Random random) {
    for (int monkey = from; monkey < to; monkey++) {
      if (random.nextDouble() < settings.perturbation()) {
        troop.set(monkey, new TrackedCost(problem, greedy.solve(problem, budget, random)));
      } else {
        follow(problem, troop, monkey, global, global, budget, random);
      }
    }
  }

  // between 0.1 (as bad as can be) and 1 (as good as the global leader)
  private static double quality(TrackedCost monkey, TrackedCost global) {
    return 0.1 + 0.9 * (1 + global.total()) / (1 + monkey.total());
  }

  private static TrackedCost best(List<TrackedCost> troop, int from, int to) {
    TrackedCost best = troop.get(from);
    for (int monkey = from + 1; monkey < to; monkey++) {
      if (troop.get(monkey).total() < best.total()) {
        best = troop.get(monkey);
      }
    }
    return best;
  }

}
