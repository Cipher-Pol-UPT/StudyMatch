package com.studymatch.matching;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

// the Bees Algorithm (Pham): scouts find sites, the best sites get the most recruits; a site's neighbourhood shrinks
// while it stops improving and the site is left once it can't shrink anymore
public final class BeesAlgorithmMatching implements MatchingAlgorithm {

  // sites: scouts; selected: sites that get recruits; elite: the best of those, with eliteBees each, the rest with
  // otherBees each; patchSize: random steps a recruit takes at first; stagnationLimit: shrunk rounds before leaving
  public record Settings(int sites, int selected, int elite, int eliteBees, int otherBees, int patchSize,
      int stagnationLimit) {
  }

  // from MatchingTuning
  public static final Settings DEFAULTS = new Settings(10, 6, 2, 8, 3, 6, 10);

  private static final class Site {

    private TrackedCost grouping;
    private int patch;
    private int stagnant;

    Site(TrackedCost grouping, int patch) {
      this.grouping = grouping;
      this.patch = patch;
    }

  }

  private final GreedyMatching greedy = new GreedyMatching();
  private final Settings settings;

  public BeesAlgorithmMatching() {
    this(DEFAULTS);
  }

  public BeesAlgorithmMatching(Settings settings) {
    this.settings = settings;
  }

  @Override
  public Grouping solve(MatchingProblem problem, MatchingBudget budget, Random random) {
    List<Site> sites = new ArrayList<>();
    for (int site = 0; site < settings.sites(); site++) {
      sites.add(scout(problem, budget, random));
    }
    if (problem.getGroupCount() < 2) {
      return sites.getFirst().grouping.grouping();
    }
    Grouping best = null;
    double bestCost = Double.POSITIVE_INFINITY;

    while (!budget.isExhausted()) {
      sites.sort(Comparator.comparingDouble(site -> site.grouping.total()));
      if (sites.getFirst().grouping.total() < bestCost) {
        best = sites.getFirst().grouping.grouping();
        bestCost = sites.getFirst().grouping.total();
      }
      for (int rank = 0; rank < settings.selected() && !budget.isExhausted(); rank++) {
        int recruits = rank < settings.elite() ? settings.eliteBees() : settings.otherBees();
        search(problem, sites, rank, recruits, budget, random);
      }
      // the other scouts look somewhere new
      for (int rank = settings.selected(); rank < sites.size(); rank++) {
        sites.set(rank, scout(problem, budget, random));
      }
    }
    return best != null ? best : sites.getFirst().grouping.grouping();
  }

  // the recruits each take patch random steps from the site; the best one moves the site, otherwise it shrinks
  private void search(MatchingProblem problem, List<Site> sites, int index, int recruits, MatchingBudget budget,
      Random random) {
    Site site = sites.get(index);
    TrackedCost bestRecruit = null;
    for (int bee = 0; bee < recruits; bee++) {
      TrackedCost recruit = new TrackedCost(problem, site.grouping.grouping());
      for (int step = 0; step < site.patch && budget.spend(); step++) {
        Step.applyRandom(problem, recruit, random);
      }
      if (bestRecruit == null || recruit.total() < bestRecruit.total()) {
        bestRecruit = recruit;
      }
    }
    if (bestRecruit != null && bestRecruit.total() < site.grouping.total()) {
      site.grouping = bestRecruit;
      site.stagnant = 0;
    } else if (site.patch > 1) {
      site.patch--;
    } else if (++site.stagnant > settings.stagnationLimit()) {
      sites.set(index, scout(problem, budget, random));
    }
  }

  private Site scout(MatchingProblem problem, MatchingBudget budget, Random random) {
    return new Site(new TrackedCost(problem, greedy.solve(problem, budget, random)), settings.patchSize());
  }

}
