package com.studymatch.matching;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

// Imperialist Competitive Algorithm (Atashpaz-Gargari & Lucas): the best countries rule empires of colonies;
// colonies copy pairings from their imperialist and sometimes revolt, a colony better than its imperialist takes
// over, and empires compete for the weakest colonies until the weak ones collapse
public final class ImperialistCompetitiveMatching implements MatchingAlgorithm {

  // countries: total; empires: imperialists at the start; assimilation: pairings a colony copies per round;
  // revolution: chance a colony takes a random step instead
  public record Settings(int countries, int empires, int assimilation, double revolution) {
  }

  // from MatchingTuning
  public static final Settings DEFAULTS = new Settings(30, 3, 2, 0.1);

  private static final class Empire {

    private TrackedCost imperialist;
    private final List<TrackedCost> colonies = new ArrayList<>();

    Empire(TrackedCost imperialist) {
      this.imperialist = imperialist;
    }

    // the imperialist counts fully, the colonies a tenth, as in the paper
    double power() {
      return imperialist.total() + 0.1 * colonies.stream().mapToDouble(TrackedCost::total).average().orElse(0);
    }

  }

  private final GreedyMatching greedy = new GreedyMatching();
  private final Settings settings;

  public ImperialistCompetitiveMatching() {
    this(DEFAULTS);
  }

  public ImperialistCompetitiveMatching(Settings settings) {
    this.settings = settings;
  }

  @Override
  public Grouping solve(MatchingProblem problem, MatchingBudget budget, Random random) {
    List<TrackedCost> countries = new ArrayList<>();
    for (int country = 0; country < settings.countries(); country++) {
      countries.add(new TrackedCost(problem, greedy.solve(problem, budget, random)));
    }
    if (problem.getGroupCount() < 2) {
      return countries.getFirst().grouping();
    }
    countries.sort(Comparator.comparingDouble(TrackedCost::total));
    List<Empire> empires = new ArrayList<>();
    for (int empire = 0; empire < settings.empires(); empire++) {
      empires.add(new Empire(countries.get(empire)));
    }
    // colonies dealt round robin, so the stronger empires get the better ones
    for (int country = settings.empires(); country < countries.size(); country++) {
      empires.get((country - settings.empires()) % empires.size()).colonies.add(countries.get(country));
    }

    while (!budget.isExhausted()) {
      for (Empire empire : empires) {
        for (int colony = 0; colony < empire.colonies.size() && !budget.isExhausted(); colony++) {
          assimilate(problem, empire.colonies.get(colony), empire.imperialist, budget, random);
          TrackedCost candidate = empire.colonies.get(colony);
          if (candidate.total() < empire.imperialist.total()) {
            empire.colonies.set(colony, empire.imperialist);
            empire.imperialist = candidate;
          }
        }
      }
      compete(empires, random);
    }
    return empires.stream().map(empire -> empire.imperialist).min(Comparator.comparingDouble(TrackedCost::total))
        .orElseThrow().grouping();
  }

  // the colony copies pairings from its imperialist, or revolts with a random step
  private void assimilate(MatchingProblem problem, TrackedCost colony, TrackedCost imperialist,
      MatchingBudget budget, Random random) {
    if (random.nextDouble() < settings.revolution()) {
      if (budget.spend()) {
        Step.applyRandom(problem, colony, random);
      }
      return;
    }
    for (int pairing = 0; pairing < settings.assimilation(); pairing++) {
      Step step = Step.learnPair(problem, colony, imperialist, random);
      if (step != null && step.allowed(problem, colony) && budget.spend()) {
        step.apply(colony);
      }
    }
  }

  // the weakest colony of the weakest empire goes to another empire; an empire with no colonies collapses
  private static void compete(List<Empire> empires, Random random) {
    if (empires.size() < 2) {
      return;
    }
    Empire weakest = empires.stream().max(Comparator.comparingDouble(Empire::power)).orElseThrow();
    if (weakest.colonies.isEmpty()) {
      empires.remove(weakest);
      empires.get(random.nextInt(empires.size())).colonies.add(weakest.imperialist);
      return;
    }
    TrackedCost lost = weakest.colonies.stream().max(Comparator.comparingDouble(TrackedCost::total)).orElseThrow();
    weakest.colonies.remove(lost);
    List<Empire> others = new ArrayList<>(empires);
    others.remove(weakest);
    others.get(random.nextInt(others.size())).colonies.add(lost);
  }

}
