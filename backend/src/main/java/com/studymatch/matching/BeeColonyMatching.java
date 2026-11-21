package com.studymatch.matching;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;

// Artificial Bee Colony (Karaboga): food sources are groupings; employed bees try a change on their own source,
// onlookers visit the better sources more often, and a source nobody improves for a while is left for a new one
public final class BeeColonyMatching implements MatchingAlgorithm {

  // sources: food sources (and employed bees); limit: tries without improving before a scout replaces a source;
  // partnerShare: changes that copy a pairing from another source instead of a random step;
  // moveShare: random steps that move a student instead of swapping two;
  // targetedShare: random steps that start from a group paying a penalty;
  // scoutKick: above 0 a scout starts from the best source this many random steps away, instead of from greedy;
  // rankedOnlookers: onlookers pick by rank instead of by 1 / (1 + cost), which barely separates close costs;
  // temperature: above 0 a bee sometimes keeps a worse change, like annealing, cooling to a thousandth by the end
  public record Settings(int sources, int limit, double partnerShare, double moveShare, double targetedShare,
      int scoutKick, boolean rankedOnlookers, double temperature) {

    // the first version, without the additions
    public Settings(int sources, int limit, double partnerShare, double moveShare) {
      this(sources, limit, partnerShare, moveShare, 0, 0, false, 0);
    }

    // without a temperature
    public Settings(int sources, int limit, double partnerShare, double moveShare, double targetedShare,
        int scoutKick, boolean rankedOnlookers) {
      this(sources, limit, partnerShare, moveShare, targetedShare, scoutKick, rankedOnlookers, 0);
    }

    public Settings withTemperature(double value) {
      return new Settings(sources, limit, partnerShare, moveShare, targetedShare, scoutKick, rankedOnlookers, value);
    }

  }

  // from MatchingTuning: a long limit mattered most (100 was far worse), learning from partners helped; on 80
  // students fewer sources and swaps only were better (moves hurt here like they did for the ants)
  public static final Settings DEFAULTS = new Settings(10, 3_000, 0.9, 0);

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
    TrackedCost best = sources.getFirst();
    Grouping bestGrouping = null;
    double bestCost = Double.POSITIVE_INFINITY;

    while (!budget.isExhausted()) {
      // employed bees, one per source
      for (int source = 0; source < sources.size(); source++) {
        trials[source] = forage(problem, sources, source, budget, random) ? 0 : trials[source] + 1;
      }
      // onlookers, more often to the better sources
      for (int onlooker = 0; onlooker < sources.size(); onlooker++) {
        int source = settings.rankedOnlookers() ? chooseByRank(sources, random) : chooseByQuality(sources, random);
        trials[source] = forage(problem, sources, source, budget, random) ? 0 : trials[source] + 1;
      }
      for (TrackedCost food : sources) {
        if (food.total() < bestCost) {
          best = food;
          bestGrouping = food.grouping();
          bestCost = food.total();
        }
      }
      // scouts: an exhausted source is left for a new one
      for (int source = 0; source < sources.size(); source++) {
        if (trials[source] > settings.limit()) {
          sources.set(source, scout(problem, best, budget, random));
          trials[source] = 0;
        }
      }
    }
    return bestGrouping != null ? bestGrouping : sources.getFirst().grouping();
  }

  // one bee tries one change on the source and keeps it if it's better; true when it was
  private boolean forage(MatchingProblem problem, List<TrackedCost> sources, int index, MatchingBudget budget,
      Random random) {
    TrackedCost source = sources.get(index);
    Step step = random.nextDouble() < settings.partnerShare()
        ? Step.learnPair(problem, source, sources.get(random.nextInt(sources.size())), random)
        : randomStep(problem, source, random);
    if (step == null || !step.allowed(problem, source) || !budget.spend()) {
      return false;
    }
    double before = source.total();
    double after = step.apply(source);
    if (after < before) {
      return true;
    }
    // the hybrid: a worse change is sometimes kept, it just doesn't count as an improvement
    if (settings.temperature() > 0 && random.nextDouble() < Math.exp(-(after - before) / temperature(budget))) {
      return false;
    }
    step.undo(source);
    return false;
  }

  private double temperature(MatchingBudget budget) {
    return settings.temperature() * Math.pow(0.001, budget.progress());
  }

  private Step randomStep(MatchingProblem problem, TrackedCost source, Random random) {
    // the extra draw only when targeting is on, so the first version's runs stay the same
    int student = settings.targetedShare() > 0 && random.nextDouble() < settings.targetedShare()
        ? source.memberOfCostlyGroup(random)
        : random.nextInt(problem.size());
    if (random.nextDouble() < settings.moveShare()) {
      return new Step.Move(student, source.groupOf(student), random.nextInt(problem.getGroupCount()));
    }
    return new Step.Swap(student, random.nextInt(problem.size()));
  }

  // a new source: from greedy, or a few random steps away from the best one, which keeps what was learned
  private TrackedCost scout(MatchingProblem problem, TrackedCost best, MatchingBudget budget, Random random) {
    if (settings.scoutKick() > 0) {
      return Kick.apply(problem, best, settings.scoutKick(), budget, random);
    }
    return new TrackedCost(problem, greedy.solve(problem, budget, random));
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

  // linear ranking: the best source has weight n, the worst 1, whatever their costs are
  private static int chooseByRank(List<TrackedCost> sources, Random random) {
    List<Integer> ranked = IntStream.range(0, sources.size()).boxed()
        .sorted(Comparator.comparingDouble(index -> sources.get(index).total())).toList();
    int n = sources.size();
    int spin = random.nextInt(n * (n + 1) / 2);
    for (int rank = 0; rank < n; rank++) {
      spin -= n - rank;
      if (spin < 0) {
        return ranked.get(rank);
      }
    }
    return ranked.getLast();
  }

}
