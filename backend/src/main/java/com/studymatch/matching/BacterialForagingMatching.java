package com.studymatch.matching;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

// Bacterial Foraging (Passino): bacteria tumble and keep swimming while it helps, the healthiest half splits after
// a while and the other half dies, and now and then some are carried somewhere new
public final class BacterialForagingMatching implements MatchingAlgorithm {

  // bacteria: population (even); chemotaxis: tumbles each one makes before reproducing; swims: longest swim;
  // reproductions: rounds of reproduction before a dispersal; dispersal: chance to be carried somewhere new
  public record Settings(int bacteria, int chemotaxis, int swims, int reproductions, double dispersal) {
  }

  // from MatchingTuning
  public static final Settings DEFAULTS = new Settings(10, 20, 4, 4, 0.4);

  private final GreedyMatching greedy = new GreedyMatching();
  private final Settings settings;

  public BacterialForagingMatching() {
    this(DEFAULTS);
  }

  public BacterialForagingMatching(Settings settings) {
    this.settings = settings;
  }

  @Override
  public Grouping solve(MatchingProblem problem, MatchingBudget budget, Random random) {
    List<TrackedCost> bacteria = new ArrayList<>();
    for (int bacterium = 0; bacterium < settings.bacteria(); bacterium++) {
      bacteria.add(newBacterium(problem, budget, random));
    }
    if (problem.getGroupCount() < 2) {
      return bacteria.getFirst().grouping();
    }
    Grouping best = null;
    double bestCost = Double.POSITIVE_INFINITY;

    while (!budget.isExhausted()) {
      for (int reproduction = 0; reproduction < settings.reproductions() && !budget.isExhausted(); reproduction++) {
        double[] health = new double[bacteria.size()];
        for (int round = 0; round < settings.chemotaxis(); round++) {
          for (int bacterium = 0; bacterium < bacteria.size(); bacterium++) {
            health[bacterium] += forage(problem, bacteria.get(bacterium), budget, random);
          }
        }
        for (TrackedCost bacterium : bacteria) {
          if (bacterium.total() < bestCost) {
            best = bacterium.grouping();
            bestCost = bacterium.total();
          }
        }
        reproduce(problem, bacteria, health);
      }
      // elimination and dispersal
      for (int bacterium = 0; bacterium < bacteria.size(); bacterium++) {
        if (random.nextDouble() < settings.dispersal()) {
          bacteria.set(bacterium, newBacterium(problem, budget, random));
        }
      }
    }
    return best != null ? best : bacteria.getFirst().grouping();
  }

  // a tumble, then swimming on while each step helps; returns the cost it ended at, for its health
  private double forage(MatchingProblem problem, TrackedCost bacterium, MatchingBudget budget, Random random) {
    double cost = bacterium.total();
    for (int swim = 0; swim <= settings.swims(); swim++) {
      Step step = Step.random(problem, bacterium, random);
      if (!step.allowed(problem, bacterium) || !budget.spend()) {
        break;
      }
      double changed = step.apply(bacterium);
      if (changed >= cost) {
        step.undo(bacterium);
        break;
      }
      cost = changed;
    }
    return cost;
  }

  // the healthier half (lowest total cost over the round) splits in two, the rest die
  private static void reproduce(MatchingProblem problem, List<TrackedCost> bacteria, double[] health) {
    List<Integer> order = new ArrayList<>();
    for (int bacterium = 0; bacterium < bacteria.size(); bacterium++) {
      order.add(bacterium);
    }
    order.sort(Comparator.comparingDouble(bacterium -> health[bacterium]));
    List<TrackedCost> next = new ArrayList<>();
    for (int index = 0; index < bacteria.size() / 2; index++) {
      TrackedCost healthy = bacteria.get(order.get(index));
      next.add(healthy);
      next.add(new TrackedCost(problem, healthy.grouping()));
    }
    if (bacteria.size() % 2 == 1) {
      next.add(bacteria.get(order.get(bacteria.size() / 2)));
    }
    bacteria.clear();
    bacteria.addAll(next);
  }

  private TrackedCost newBacterium(MatchingProblem problem, MatchingBudget budget, Random random) {
    return new TrackedCost(problem, greedy.solve(problem, budget, random));
  }

}
