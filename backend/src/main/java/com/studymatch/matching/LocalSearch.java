package com.studymatch.matching;

import java.util.Random;
import java.util.function.BooleanSupplier;

// polishes a grouping with small steps, keeping the ones that lower the cost; with a temperature it sometimes keeps
// a worse one too, like annealing, and with a tabu tenure it hands over to the tabu search
final class LocalSearch {

  // steps: cost calculations per polish; moveShare: steps that move one student instead of swapping two;
  // targetedShare: steps that start from a group paying a penalty; temperature: above 0 a worse step is sometimes
  // kept, cooling to a thousandth by the last one; rotationShare: steps that rotate three students;
  // tabuTenure: above 0 it's a tabu search instead; tabuSample: swaps the tabu search looks at per step
  record Settings(int steps, double moveShare, double targetedShare, double temperature, double rotationShare,
      int tabuTenure, int tabuSample) {

    Settings withSteps(int value) {
      return new Settings(value, moveShare, targetedShare, temperature, rotationShare, tabuTenure, tabuSample);
    }

    Settings withMoveShare(double value) {
      return new Settings(steps, value, targetedShare, temperature, rotationShare, tabuTenure, tabuSample);
    }

    Settings withTargetedShare(double value) {
      return new Settings(steps, moveShare, value, temperature, rotationShare, tabuTenure, tabuSample);
    }

    Settings withTemperature(double value) {
      return new Settings(steps, moveShare, targetedShare, value, rotationShare, tabuTenure, tabuSample);
    }

    Settings withRotationShare(double value) {
      return new Settings(steps, moveShare, targetedShare, temperature, value, tabuTenure, tabuSample);
    }

    Settings withTabu(int tenure, int sample) {
      return new Settings(steps, moveShare, targetedShare, temperature, rotationShare, tenure, sample);
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
    if (settings.tabuTenure() > 0) {
      // every tabu step looks at tabuSample swaps, so this many steps uses about the same calculations
      int tabuSteps = Math.max(1, settings.steps() / settings.tabuSample());
      TabuSearch.Outcome outcome = TabuSearch.search(problem, grouping, tabuSteps, Integer.MAX_VALUE,
          settings.tabuTenure(), settings.tabuSample(), settings.targetedShare(), random, mayStep);
      return new Outcome(outcome.best(), outcome.evaluations());
    }
    return descend(problem, grouping, random, mayStep);
  }

  private Outcome descend(MatchingProblem problem, Grouping grouping, Random random, BooleanSupplier mayStep) {
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
      Step candidate = randomStep(problem, tracked, random);
      if (!candidate.allowed(problem, tracked)) {
        continue;
      }
      if (!mayStep.getAsBoolean()) {
        break;
      }
      evaluations++;
      double changed = candidate.apply(tracked);
      if (accepts(changed, cost, temperature, random)) {
        cost = changed;
        if (temperature > 0 && cost < bestCost) {
          best = tracked.grouping();
          bestCost = cost;
        }
      } else {
        candidate.undo(tracked);
      }
    }
    // after keeping worse steps the end can be worse than a point on the way, so go back to the best one
    if (bestCost < tracked.total()) {
      tracked = new TrackedCost(problem, best);
    }
    return new Outcome(tracked, evaluations);
  }

  // a student (often from a costly group) and a random other: a move, a rotation with a third, or a swap
  private Step randomStep(MatchingProblem problem, TrackedCost tracked, Random random) {
    int student = random.nextDouble() < settings.targetedShare() ? tracked.memberOfCostlyGroup(random)
        : random.nextInt(problem.size());
    int other = random.nextInt(problem.size());
    boolean move = random.nextDouble() < settings.moveShare();
    if (!move && random.nextDouble() < settings.rotationShare()) {
      return new Step.Rotation(student, other, random.nextInt(problem.size()));
    }
    if (move) {
      return new Step.Move(student, tracked.groupOf(student), tracked.groupOf(other));
    }
    return new Step.Swap(student, other);
  }

  // better always; worse with the annealing chance while there's a temperature
  private static boolean accepts(double changed, double cost, double temperature, Random random) {
    return changed < cost || temperature > 0 && random.nextDouble() < Math.exp(-(changed - cost) / temperature);
  }

}
