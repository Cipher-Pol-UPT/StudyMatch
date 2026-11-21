package com.studymatch.matching;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

// Clonal Selection, CLONALG (de Castro & Von Zuben), from the immune system: the best groupings are cloned, better
// ones more often; clones mutate, better ones less; the best clone replaces its parent and the worst are replaced
public final class ClonalSelectionMatching implements MatchingAlgorithm {

  // population: groupings kept; selected: how many of the best get cloned; cloneFactor: clones = population *
  // factor / rank; decay: how fast mutation drops for better parents; replaced: worst ones swapped for new ones
  public record Settings(int population, int selected, double cloneFactor, double decay, int replaced) {
  }

  // from MatchingTuning: more clones and a faster mutation decay were better
  public static final Settings DEFAULTS = new Settings(20, 10, 2, 3, 2);

  private final GreedyMatching greedy = new GreedyMatching();
  private final Settings settings;

  public ClonalSelectionMatching() {
    this(DEFAULTS);
  }

  public ClonalSelectionMatching(Settings settings) {
    this.settings = settings;
  }

  @Override
  public Grouping solve(MatchingProblem problem, MatchingBudget budget, Random random) {
    List<TrackedCost> population = new ArrayList<>();
    for (int cell = 0; cell < settings.population(); cell++) {
      population.add(new TrackedCost(problem, greedy.solve(problem, budget, random)));
    }
    if (problem.getGroupCount() < 2) {
      return population.getFirst().grouping();
    }
    // at most a tenth of the class changes in one mutation
    int maxMutation = Math.max(1, problem.size() / 10);

    while (!budget.isExhausted()) {
      population.sort(Comparator.comparingDouble(TrackedCost::total));
      int selected = Math.min(settings.selected(), population.size());
      for (int rank = 0; rank < selected && !budget.isExhausted(); rank++) {
        double quality = 1 - (double) rank / selected;
        int mutation = 1 + (int) (maxMutation * Math.exp(-settings.decay() * quality));
        int clones = Math.max(1, (int) Math.round(settings.cloneFactor() * population.size() / (rank + 1)));
        TrackedCost bestClone = bestClone(problem, population.get(rank), clones, mutation, budget, random);
        if (bestClone.total() < population.get(rank).total()) {
          population.set(rank, bestClone);
        }
      }
      population.sort(Comparator.comparingDouble(TrackedCost::total));
      for (int cell = population.size() - settings.replaced(); cell < population.size(); cell++) {
        population.set(cell, new TrackedCost(problem, greedy.solve(problem, budget, random)));
      }
    }
    population.sort(Comparator.comparingDouble(TrackedCost::total));
    return population.getFirst().grouping();
  }

  private static TrackedCost bestClone(MatchingProblem problem, TrackedCost parent, int clones, int mutation,
      MatchingBudget budget, Random random) {
    TrackedCost best = null;
    for (int clone = 0; clone < clones; clone++) {
      TrackedCost copy = new TrackedCost(problem, parent.grouping());
      for (int step = 0; step < mutation && budget.spend(); step++) {
        Step.applyRandom(problem, copy, random);
      }
      if (best == null || copy.total() < best.total()) {
        best = copy;
      }
    }
    return best;
  }

}
