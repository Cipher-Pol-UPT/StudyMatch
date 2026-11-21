package com.studymatch.matching;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

// Artificial Bee Colony (Karaboga) with an annealing-style acceptance: food sources are groupings; employed bees try
// a change on their own source, onlookers visit the better sources more often, and a source nobody improves for a
// while is left for a new one. A change is a swap or a pairing copied from another source, and a worse one is
// sometimes kept, with a chance that cools over the budget
public final class BeeColonyMatching implements MatchingAlgorithm {

  // sources: food sources (and employed bees); limit: tries without improving before a scout replaces a source;
  // partnerShare: changes that copy a pairing from another source instead of a random swap;
  // temperature: above 0 a worse change is sometimes kept, cooling to a thousandth by the end
  public record Settings(int sources, int limit, double partnerShare, double temperature) {

    public Settings withTemperature(double value) {
      return new Settings(sources, limit, partnerShare, value);
    }

  }

  // from MatchingTuning; see docs/group-matching.md
  public static final Settings DEFAULTS = new Settings(10, 3_000, 0.9, 0.2);

  private final GreedyMatching greedy = new GreedyMatching();
  private final Settings settings;

  public BeeColonyMatching() {
    this(DEFAULTS);
  }

  public BeeColonyMatching(Settings settings) {
    this.settings = settings;
  }

  @Override
  public Grouping solve(MatchingProblem problem, MatchingBudget budget, Random random) {
    List<TrackedCost> sources = new ArrayList<>();
    for (int source = 0; source < settings.sources(); source++) {
      sources.add(new TrackedCost(problem, greedy.solve(problem, budget, random)));
    }
    if (problem.getGroupCount() < 2) {
      return sources.getFirst().grouping();
    }
    int[] trials = new int[sources.size()];
    Grouping best = sources.getFirst().grouping();
    double bestCost = sources.getFirst().total();

    while (!budget.isExhausted()) {
      forageRound(problem, sources, trials, budget, random);
      TrackedCost cheapest = cheapest(sources);
      if (cheapest.total() < bestCost) {
        best = cheapest.grouping();
        bestCost = cheapest.total();
      }
      scout(problem, sources, trials, budget, random);
    }
    return best;
  }

  // employed bees, one per source, then the onlookers, more often to the better sources
  private void forageRound(MatchingProblem problem, List<TrackedCost> sources, int[] trials, MatchingBudget budget,
      Random random) {
    for (int source = 0; source < sources.size(); source++) {
      trials[source] = forage(problem, sources, source, budget, random) ? 0 : trials[source] + 1;
    }
    for (int onlooker = 0; onlooker < sources.size(); onlooker++) {
      int source = chooseByQuality(sources, random);
      trials[source] = forage(problem, sources, source, budget, random) ? 0 : trials[source] + 1;
    }
  }

  // scouts: a source nobody improved for limit tries is left for a new one
  private void scout(MatchingProblem problem, List<TrackedCost> sources, int[] trials, MatchingBudget budget,
      Random random) {
    for (int source = 0; source < sources.size(); source++) {
      if (trials[source] > settings.limit()) {
        sources.set(source, new TrackedCost(problem, greedy.solve(problem, budget, random)));
        trials[source] = 0;
      }
    }
  }

  private static TrackedCost cheapest(List<TrackedCost> sources) {
    TrackedCost cheapest = sources.getFirst();
    for (TrackedCost source : sources) {
      if (source.total() < cheapest.total()) {
        cheapest = source;
      }
    }
    return cheapest;
  }

  // one bee tries one change on the source; true when it made it better
  private boolean forage(MatchingProblem problem, List<TrackedCost> sources, int index, MatchingBudget budget,
      Random random) {
    TrackedCost source = sources.get(index);
    Step step = random.nextDouble() < settings.partnerShare()
        ? Step.learnPair(problem, source, sources.get(random.nextInt(sources.size())), random)
        : new Step.Swap(random.nextInt(problem.size()), random.nextInt(problem.size()));
    if (step == null || !step.allowed(problem, source) || !budget.spend()) {
      return false;
    }
    double before = source.total();
    double after = step.apply(source);
    if (after < before) {
      return true;
    }
    // a worse change is sometimes kept, it just doesn't count as an improvement
    if (!keepsWorse(after - before, budget, random)) {
      step.undo(source);
    }
    return false;
  }

  private boolean keepsWorse(double increase, MatchingBudget budget, Random random) {
    if (settings.temperature() <= 0) {
      return false;
    }
    double temperature = settings.temperature() * Math.pow(0.001, budget.progress());
    return random.nextDouble() < Math.exp(-increase / temperature);
  }

  // roulette by 1 / (1 + cost), so cheaper sources get more onlookers
  private static int chooseByQuality(List<TrackedCost> sources, Random random) {
    double total = 0;
    for (TrackedCost source : sources) {
      total += 1 / (1 + source.total());
    }
    double spin = random.nextDouble() * total;
    for (int index = 0; index < sources.size(); index++) {
      spin -= 1 / (1 + sources.get(index).total());
      if (spin < 0) {
        return index;
      }
    }
    return sources.size() - 1;
  }

}
