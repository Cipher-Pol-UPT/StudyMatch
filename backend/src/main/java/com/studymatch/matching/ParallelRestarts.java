package com.studymatch.matching;

import java.util.List;
import java.util.Random;
import java.util.function.Supplier;
import java.util.stream.IntStream;

// runs independent copies of an algorithm at once, one per core, and keeps the cheapest result
public final class ParallelRestarts implements MatchingAlgorithm {

  private final Supplier<MatchingAlgorithm> algorithm;
  private final int copies;

  public ParallelRestarts(Supplier<MatchingAlgorithm> algorithm, int copies) {
    this.algorithm = algorithm;
    this.copies = copies;
  }

  @Override
  public Grouping solve(MatchingProblem problem, MatchingBudget budget, Random random) {
    // seeds drawn first, so the result doesn't depend on which thread finishes first
    long[] seeds = random.longs(copies).toArray();
    List<MatchingBudget> budgets = budget.split(copies);
    List<Grouping> results = IntStream.range(0, copies).parallel()
        .mapToObj(copy -> algorithm.get().solve(problem, budgets.get(copy), new Random(seeds[copy])))
        .toList();
    budgets.forEach(budget::addUsed);

    Grouping best = results.getFirst();
    double bestCost = MatchingCost.cost(problem, best);
    for (Grouping result : results.subList(1, results.size())) {
      double cost = MatchingCost.cost(problem, result);
      if (cost < bestCost) {
        best = result;
        bestCost = cost;
      }
    }
    return best;
  }

}
