package com.studymatch.matching;

import java.util.Random;
import java.util.function.BooleanSupplier;

// polishes a grouping with swaps, keeping the ones that lower the cost; with a temperature it sometimes keeps a
// worse one too, like annealing
final class LocalSearch {

  // steps: cost calculations per polish; targetedShare: swaps that start from a group paying a penalty;
  // temperature: above 0 a worse swap is sometimes kept, cooling to a thousandth by the last step
  record Settings(int steps, double targetedShare, double temperature) {

    Settings withSteps(int value) {
      return new Settings(value, targetedShare, temperature);
    }

    Settings withTemperature(double value) {
      return new Settings(steps, targetedShare, value);
    }

  }

  // the polished grouping and how many cost calculations it took
  record Outcome(TrackedCost polished, int evaluations) {
  }

  private final Settings settings;

  LocalSearch(Settings settings) {
    this.settings = settings;
  }

  // mayStep is asked before every cost calculation: the budget, or always yes when the work is charged afterwards
  Outcome polish(MatchingProblem problem, Grouping grouping, Random random, BooleanSupplier mayStep) {
    TrackedCost tracked = new TrackedCost(problem, grouping);
    double cost = tracked.total();
    // the grouping given isn't changed, TrackedCost works on its own copy
    Grouping best = grouping;
    double bestCost = cost;
    double temperature = settings.temperature();
    double cooling = Math.pow(0.001, 1.0 / Math.max(1, settings.steps()));
    int evaluations = 0;

    for (int step = 0; step < settings.steps(); step++) {
      temperature *= cooling;
      Step swap = new Step.Swap(pickStudent(problem, tracked, random), random.nextInt(problem.size()));
      if (!swap.allowed(problem, tracked)) {
        continue;
      }
      if (!mayStep.getAsBoolean()) {
        break;
      }
      evaluations++;
      double changed = swap.apply(tracked);
      if (accepts(changed, cost, temperature, random)) {
        cost = changed;
        if (temperature > 0 && cost < bestCost) {
          best = tracked.grouping();
          bestCost = cost;
        }
      } else {
        swap.undo(tracked);
      }
    }
    // after keeping worse swaps the end can be worse than a point on the way, so go back to the best one
    if (bestCost < tracked.total()) {
      tracked = new TrackedCost(problem, best);
    }
    return new Outcome(tracked, evaluations);
  }

  // often a member of a group paying a penalty, otherwise anyone
  private int pickStudent(MatchingProblem problem, TrackedCost tracked, Random random) {
    return random.nextDouble() < settings.targetedShare() ? tracked.memberOfCostlyGroup(random)
        : random.nextInt(problem.size());
  }

  // better always; worse with the annealing chance while there's a temperature
  private static boolean accepts(double changed, double cost, double temperature, Random random) {
    return changed < cost || temperature > 0 && random.nextDouble() < Math.exp(-(changed - cost) / temperature);
  }

}
