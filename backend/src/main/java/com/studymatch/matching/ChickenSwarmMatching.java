package com.studymatch.matching;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

// Chicken Swarm Optimization (Meng et al.): roosters search on their own, hens follow their rooster and steal from
// better chickens, chicks follow their mother; the pecking order is redone every few rounds
public final class ChickenSwarmMatching implements MatchingAlgorithm {

  // chickens: flock size; roosterShare, henShare: the best shares that are roosters and hens (the rest are
  // chicks); regroup: rounds between new pecking orders
  public record Settings(int chickens, double roosterShare, double henShare, int regroup) {
  }

  // from MatchingTuning, on 80 students
  public static final Settings DEFAULTS = new Settings(40, 0.2, 0.6, 20);

  private final GreedyMatching greedy = new GreedyMatching();
  private final Settings settings;

  public ChickenSwarmMatching() {
    this(DEFAULTS);
  }

  public ChickenSwarmMatching(Settings settings) {
    this.settings = settings;
  }

  @Override
  public Grouping solve(MatchingProblem problem, MatchingBudget budget, Random random) {
    List<TrackedCost> flock = new ArrayList<>();
    for (int chicken = 0; chicken < settings.chickens(); chicken++) {
      flock.add(new TrackedCost(problem, greedy.solve(problem, budget, random)));
    }
    if (problem.getGroupCount() < 2) {
      return flock.getFirst().grouping();
    }
    int roosters = Math.max(1, (int) (settings.roosterShare() * flock.size()));
    int hens = Math.max(1, (int) (settings.henShare() * flock.size()));
    int[] leader = new int[flock.size()];

    for (int round = 0; !budget.isExhausted(); round++) {
      if (round % settings.regroup() == 0) {
        flock.sort(Comparator.comparingDouble(TrackedCost::total));
        // every hen gets a rooster, every chick a mother hen
        for (int chicken = roosters; chicken < flock.size(); chicken++) {
          leader[chicken] = chicken < roosters + hens ? random.nextInt(roosters) : roosters + random.nextInt(hens);
        }
      }
      for (int chicken = 0; chicken < flock.size() && !budget.isExhausted(); chicken++) {
        TrackedCost current = flock.get(chicken);
        double before = current.total();
        List<Step> taken = new ArrayList<>(2);
        if (chicken < roosters) {
          take(problem, current, Step.random(problem, current, random), taken, budget);
        } else {
          take(problem, current, Step.learnPair(problem, current, flock.get(leader[chicken]), random), taken, budget);
          if (chicken < roosters + hens) {
            take(problem, current, Step.learnPair(problem, current, flock.get(random.nextInt(chicken)), random),
                taken, budget);
          }
        }
        // in place, so a move that didn't help is taken back
        if (current.total() >= before) {
          for (int index = taken.size() - 1; index >= 0; index--) {
            taken.get(index).undo(current);
          }
        }
      }
    }
    return flock.stream().min(Comparator.comparingDouble(TrackedCost::total)).orElseThrow().grouping();
  }

  private static void take(MatchingProblem problem, TrackedCost chicken, Step step, List<Step> taken,
      MatchingBudget budget) {
    if (step != null && step.allowed(problem, chicken) && budget.spend()) {
      step.apply(chicken);
      taken.add(step);
    }
  }

}
