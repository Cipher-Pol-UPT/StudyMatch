package com.studymatch.matching;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

// a population of student orders: better ones become parents, children mix two orders and mutate a little
public final class GeneticMatching implements MatchingAlgorithm {

  static final int POPULATION = 40;
  // the best ones go to the next generation untouched
  static final int ELITE = 2;
  static final double MUTATION_RATE = 0.2;
  // filling by order doesn't know about separations, so breaking one costs this much
  static final double SEPARATION_PENALTY = 100;

  private final GreedyMatching greedy = new GreedyMatching();
  // swaps each child tries before joining the population; above 0 this is a memetic algorithm
  private final int localSearchSteps;

  public GeneticMatching() {
    this(0);
  }

  public GeneticMatching(int localSearchSteps) {
    this.localSearchSteps = localSearchSteps;
  }

  @Override
  public Grouping solve(MatchingProblem problem, MatchingBudget budget, Random random) {
    List<int[]> population = new ArrayList<>();
    List<Double> scores = new ArrayList<>();
    for (int i = 0; i < POPULATION && budget.spend(); i++) {
      int[] order = shuffled(problem.size(), random);
      population.add(order);
      scores.add(score(problem, order));
    }
    if (population.isEmpty()) {
      return greedy.solve(problem, budget, random);
    }
    int[] best = population.get(lowest(scores));
    double bestScore = scores.get(lowest(scores));

    while (!budget.isExhausted()) {
      List<int[]> nextPopulation = new ArrayList<>();
      List<Double> nextScores = new ArrayList<>();
      List<Integer> ranked = new ArrayList<>();
      for (int i = 0; i < population.size(); i++) {
        ranked.add(i);
      }
      List<Double> currentScores = scores;
      ranked.sort((first, second) -> Double.compare(currentScores.get(first), currentScores.get(second)));
      for (int i = 0; i < Math.min(ELITE, ranked.size()); i++) {
        nextPopulation.add(population.get(ranked.get(i)));
        nextScores.add(scores.get(ranked.get(i)));
      }

      while (nextPopulation.size() < POPULATION && budget.spend()) {
        int[] child = orderCrossover(tournament(population, scores, random), tournament(population, scores, random),
            random);
        if (random.nextDouble() < MUTATION_RATE) {
          swapTwo(child, random);
        }
        double score = improve(problem, child, score(problem, child), budget, random);
        nextPopulation.add(child);
        nextScores.add(score);
        if (score < bestScore) {
          best = child;
          bestScore = score;
        }
      }
      population = nextPopulation;
      scores = nextScores;
    }

    Grouping grouping = new Grouping(problem.fill(best), problem.getGroupCount());
    SeparationRepair.repair(problem, grouping, random);
    return MatchingCost.isValid(problem, grouping) ? grouping : greedy.solve(problem, budget, random);
  }

  // order crossover (OX): keep a slice of the first parent as it is, the rest in the order of the second
  static int[] orderCrossover(int[] first, int[] second, Random random) {
    int length = first.length;
    int start = random.nextInt(length);
    int end = start + random.nextInt(length - start);
    int[] child = new int[length];
    boolean[] taken = new boolean[length];
    for (int i = start; i <= end; i++) {
      child[i] = first[i];
      taken[first[i]] = true;
    }
    int position = (end + 1) % length;
    for (int i = 0; i < length; i++) {
      int student = second[(end + 1 + i) % length];
      if (!taken[student]) {
        child[position] = student;
        position = (position + 1) % length;
      }
    }
    return child;
  }

  // two at random, the better one is a parent
  private static int[] tournament(List<int[]> population, List<Double> scores, Random random) {
    int first = random.nextInt(population.size());
    int second = random.nextInt(population.size());
    return population.get(scores.get(first) <= scores.get(second) ? first : second);
  }

  // memetic step: the child tries a few swaps and keeps the ones that help; returns its new score
  private double improve(MatchingProblem problem, int[] child, double score, MatchingBudget budget,
      Random random) {
    for (int step = 0; step < localSearchSteps && budget.spend(); step++) {
      int first = random.nextInt(child.length);
      int second = random.nextInt(child.length);
      swap(child, first, second);
      double swapped = score(problem, child);
      if (swapped < score) {
        score = swapped;
      } else {
        swap(child, first, second);
      }
    }
    return score;
  }

  private static void swap(int[] order, int first, int second) {
    int student = order[first];
    order[first] = order[second];
    order[second] = student;
  }

  // two students trade places in the order, which usually means trading groups
  private static void swapTwo(int[] order, Random random) {
    swap(order, random.nextInt(order.length), random.nextInt(order.length));
  }

  private static int[] shuffled(int size, Random random) {
    int[] order = new int[size];
    Arrays.setAll(order, i -> i);
    for (int i = size - 1; i > 0; i--) {
      int j = random.nextInt(i + 1);
      int student = order[i];
      order[i] = order[j];
      order[j] = student;
    }
    return order;
  }

  private static int lowest(List<Double> scores) {
    int lowest = 0;
    for (int i = 1; i < scores.size(); i++) {
      if (scores.get(i) < scores.get(lowest)) {
        lowest = i;
      }
    }
    return lowest;
  }

  private static double score(MatchingProblem problem, int[] order) {
    Grouping grouping = new Grouping(problem.fill(order), problem.getGroupCount());
    return MatchingCost.cost(problem, grouping) + SEPARATION_PENALTY * SeparationRepair.broken(problem, grouping);
  }

}
