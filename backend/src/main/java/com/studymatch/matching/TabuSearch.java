package com.studymatch.matching;

import java.util.Random;
import java.util.function.BooleanSupplier;

// tabu search: each step makes the best of a sample of swaps even if it's worse, then the two students can't move
// for tenure steps, so it can't just undo it; a tabu swap is still allowed if it beats the best so far
final class TabuSearch {

  // the best grouping found, how many cost calculations it took, and whether the budget ran out
  record Outcome(TrackedCost best, int evaluations, boolean outOfBudget) {
  }

  // a swap looked at during a step, and the cost it would give
  private record Candidate(Step.Swap swap, double cost) {
  }

  private final MatchingProblem problem;
  private final TrackedCost tracked;
  private final int tenure;
  private final int sample;
  private final double targetedShare;
  private final Random random;
  private final BooleanSupplier mayStep;
  // the step until which each student can't move
  private final int[] tabuUntil;
  private Grouping best;
  private double bestCost;
  private int evaluations;
  private boolean outOfBudget;

  private TabuSearch(MatchingProblem problem, Grouping start, int tenure, int sample, double targetedShare,
      Random random, BooleanSupplier mayStep) {
    this.problem = problem;
    this.tracked = new TrackedCost(problem, start);
    this.tenure = tenure;
    this.sample = sample;
    this.targetedShare = targetedShare;
    this.random = random;
    this.mayStep = mayStep;
    this.tabuUntil = new int[problem.size()];
    this.best = start;
    this.bestCost = tracked.total();
  }

  // stops after maxSteps steps, or after patience steps in a row without a new best
  static Outcome search(MatchingProblem problem, Grouping start, int maxSteps, int patience, int tenure, int sample,
      double targetedShare, Random random, BooleanSupplier mayStep) {
    return new TabuSearch(problem, start, tenure, sample, targetedShare, random, mayStep).run(maxSteps, patience);
  }

  private Outcome run(int maxSteps, int patience) {
    int stepsWithoutBetter = 0;
    for (int step = 0; step < maxSteps && stepsWithoutBetter < patience && !outOfBudget; step++) {
      Candidate chosen = bestOfSample(step);
      boolean better = chosen != null && take(chosen, step);
      stepsWithoutBetter = better ? 0 : stepsWithoutBetter + 1;
    }
    // the search ends wherever its last step went, the best point on the way can be earlier
    TrackedCost result = bestCost < tracked.total() ? new TrackedCost(problem, best) : tracked;
    return new Outcome(result, evaluations, outOfBudget);
  }

  // the best allowed swap of a sample, tried and taken back; a tabu one only if it beats the best so far
  private Candidate bestOfSample(int step) {
    Candidate chosen = null;
    for (int tried = 0; tried < sample; tried++) {
      Step.Swap swap = new Step.Swap(pickStudent(), random.nextInt(problem.size()));
      if (!swap.allowed(problem, tracked)) {
        continue;
      }
      if (!mayStep.getAsBoolean()) {
        outOfBudget = true;
        break;
      }
      evaluations++;
      double cost = swap.apply(tracked);
      swap.undo(tracked);
      boolean tabu = tabuUntil[swap.first()] > step || tabuUntil[swap.second()] > step;
      if ((!tabu || cost < bestCost) && (chosen == null || cost < chosen.cost())) {
        chosen = new Candidate(swap, cost);
      }
    }
    return chosen;
  }

  // makes the swap, even a worse one, and blocks both students; true when it's a new best
  private boolean take(Candidate chosen, int step) {
    double cost = chosen.swap().apply(tracked);
    tabuUntil[chosen.swap().first()] = step + tenure;
    tabuUntil[chosen.swap().second()] = step + tenure;
    if (cost < bestCost) {
      best = tracked.grouping();
      bestCost = cost;
      return true;
    }
    return false;
  }

  // often a member of a group paying a penalty, otherwise anyone
  private int pickStudent() {
    return random.nextDouble() < targetedShare ? tracked.memberOfCostlyGroup(random) : random.nextInt(problem.size());
  }

}
