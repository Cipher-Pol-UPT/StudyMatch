package com.studymatch.matching;

import java.util.Random;

// every algorithm searches within the same budget, so they can be compared fairly
public interface MatchingAlgorithm {

  // the best valid grouping found within the budget; with a count budget, same seed, same groups
  Grouping solve(MatchingProblem problem, MatchingBudget budget, Random random);

}
