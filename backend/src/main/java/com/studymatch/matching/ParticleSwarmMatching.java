package com.studymatch.matching;

import java.util.Random;

// Particle Swarm Optimization (Kennedy & Eberhart) on random keys: every particle is pulled towards its own best
// position and the swarm's best, with the standard constriction values
public final class ParticleSwarmMatching implements MatchingAlgorithm {

  // particles: swarm size; inertia, personal, social: the velocity weights; maxVelocity: cap per number per step
  public record Settings(int particles, double inertia, double personal, double social, double maxVelocity) {
  }

  // from MatchingTuning
  public static final Settings DEFAULTS = new Settings(30, 0.7298, 1.49618, 1.49618, 0.5);

  private final GreedyMatching greedy = new GreedyMatching();
  private final Settings settings;

  public ParticleSwarmMatching() {
    this(DEFAULTS);
  }

  public ParticleSwarmMatching(Settings settings) {
    this.settings = settings;
  }

  @Override
  public Grouping solve(MatchingProblem problem, MatchingBudget budget, Random random) {
    int students = problem.size();
    double[][] positions = new double[settings.particles()][];
    double[][] velocities = new double[settings.particles()][students];
    double[][] personalBest = new double[settings.particles()][];
    double[] personalCost = new double[settings.particles()];
    double[] swarmBest = null;
    double swarmCost = Double.POSITIVE_INFINITY;

    for (int particle = 0; particle < settings.particles() && budget.spend(); particle++) {
      positions[particle] = RandomKeys.random(students, random);
      personalBest[particle] = positions[particle].clone();
      personalCost[particle] = RandomKeys.score(problem, positions[particle]);
      if (personalCost[particle] < swarmCost) {
        swarmBest = positions[particle].clone();
        swarmCost = personalCost[particle];
      }
    }
    if (swarmBest == null) {
      return greedy.solve(problem, budget, random);
    }

    while (!budget.isExhausted()) {
      for (int particle = 0; particle < settings.particles() && positions[particle] != null; particle++) {
        fly(positions[particle], velocities[particle], personalBest[particle], swarmBest, random);
        if (!budget.spend()) {
          break;
        }
        double cost = RandomKeys.score(problem, positions[particle]);
        if (cost < personalCost[particle]) {
          personalBest[particle] = positions[particle].clone();
          personalCost[particle] = cost;
        }
        if (cost < swarmCost) {
          swarmBest = positions[particle].clone();
          swarmCost = cost;
        }
      }
    }
    Grouping result = RandomKeys.finish(problem, swarmBest, random);
    return result != null ? result : greedy.solve(problem, budget, random);
  }

  private void fly(double[] position, double[] velocity, double[] personal, double[] swarm, Random random) {
    for (int student = 0; student < position.length; student++) {
      double pull = settings.personal() * random.nextDouble() * (personal[student] - position[student])
          + settings.social() * random.nextDouble() * (swarm[student] - position[student]);
      velocity[student] = Math.clamp(settings.inertia() * velocity[student] + pull, -settings.maxVelocity(),
          settings.maxVelocity());
      position[student] = Math.clamp(position[student] + velocity[student], 0, 1);
    }
  }

}
