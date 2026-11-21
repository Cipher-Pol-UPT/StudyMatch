package com.studymatch.matching;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

// Shuffled Frog Leaping (Eusuff & Lansey): frogs are dealt into memeplexes; in each one the worst frog leaps
// towards the memeplex's best, then the global best, and is replaced if neither helps; then they all shuffle again
public final class ShuffledFrogLeapingMatching implements MatchingAlgorithm {

  // frogs: population; memeplexes: groups they're dealt into; leapsPerMemeplex: worst-frog leaps per shuffle;
  // maxLeap: most pairings a frog copies in one leap
  public record Settings(int frogs, int memeplexes, int leapsPerMemeplex, int maxLeap) {
  }

  // from MatchingTuning
  public static final Settings DEFAULTS = new Settings(20, 5, 10, 6);

  private final GreedyMatching greedy = new GreedyMatching();
  private final Settings settings;

  public ShuffledFrogLeapingMatching() {
    this(DEFAULTS);
  }

  public ShuffledFrogLeapingMatching(Settings settings) {
    this.settings = settings;
  }

  @Override
  public Grouping solve(MatchingProblem problem, MatchingBudget budget, Random random) {
    List<TrackedCost> frogs = new ArrayList<>();
    for (int frog = 0; frog < settings.frogs(); frog++) {
      frogs.add(new TrackedCost(problem, greedy.solve(problem, budget, random)));
    }
    if (problem.getGroupCount() < 2) {
      return frogs.getFirst().grouping();
    }
    Comparator<TrackedCost> byCost = Comparator.comparingDouble(TrackedCost::total);

    while (!budget.isExhausted()) {
      frogs.sort(byCost);
      TrackedCost globalBest = frogs.getFirst();
      List<List<TrackedCost>> memeplexes = new ArrayList<>();
      for (int memeplex = 0; memeplex < settings.memeplexes(); memeplex++) {
        memeplexes.add(new ArrayList<>());
      }
      // dealt like cards, so every memeplex gets good and bad frogs
      for (int frog = 0; frog < frogs.size(); frog++) {
        memeplexes.get(frog % settings.memeplexes()).add(frogs.get(frog));
      }
      for (List<TrackedCost> memeplex : memeplexes) {
        for (int leap = 0; leap < settings.leapsPerMemeplex() && !budget.isExhausted(); leap++) {
          memeplex.sort(byCost);
          memeplex.set(memeplex.size() - 1, evolveWorst(problem, memeplex, globalBest, budget, random));
        }
      }
      frogs.clear();
      memeplexes.forEach(frogs::addAll);
    }
    frogs.sort(byCost);
    return frogs.getFirst().grouping();
  }

  // the worst frog leaps towards the memeplex's best, then the global best; a new frog if neither helps
  private TrackedCost evolveWorst(MatchingProblem problem, List<TrackedCost> memeplex, TrackedCost globalBest,
      MatchingBudget budget, Random random) {
    TrackedCost worst = memeplex.getLast();
    for (TrackedCost teacher : List.of(memeplex.getFirst(), globalBest)) {
      TrackedCost leaped = leap(problem, worst, teacher, budget, random);
      if (leaped.total() < worst.total()) {
        return leaped;
      }
    }
    return new TrackedCost(problem, greedy.solve(problem, budget, random));
  }

  // a copy of the frog that takes up to maxLeap pairings from the teacher, every one a cost calculation
  private TrackedCost leap(MatchingProblem problem, TrackedCost frog, TrackedCost teacher, MatchingBudget budget,
      Random random) {
    TrackedCost copy = new TrackedCost(problem, frog.grouping());
    int pairings = 1 + random.nextInt(settings.maxLeap());
    for (int pairing = 0; pairing < pairings; pairing++) {
      Step step = Step.learnPair(problem, copy, teacher, random);
      if (step != null && step.allowed(problem, copy) && budget.spend()) {
        step.apply(copy);
      }
    }
    return copy;
  }

}
