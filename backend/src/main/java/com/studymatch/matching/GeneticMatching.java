package com.studymatch.matching;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

// a population of student orders: better ones become parents, children mix two orders and mutate a little; an order
// becomes groups by filling them in that order
public final class GeneticMatching implements MatchingAlgorithm {

  static final int POPULATION = 40;
  // the best ones go to the next generation untouched
  static final int ELITE = 2;
  static final double MUTATION_RATE = 0.2;

  // an order of the students and its score
  private record Individual(int[] order, double score) {
  }

  private final GreedyMatching greedy = new GreedyMatching();

  @Override
  public Grouping solve(MatchingProblem problem, MatchingBudget budget, Random random) {
    List<Individual> population = firstGeneration(problem, budget, random);
    if (population.isEmpty()) {
      return greedy.solve(problem, budget, random);
    }
    // the best one is always kept as elite, so the fittest at the end is the best one seen
    while (!budget.isExhausted()) {
      population = nextGeneration(problem, population, budget, random);
    }
    Grouping grouping = new Grouping(problem.fill(fittest(population).order()), problem.getGroupCount());
    SeparationRepair.repair(problem, grouping, random);
    return MatchingCost.isValid(problem, grouping) ? grouping : greedy.solve(problem, budget, random);
  }

  private static List<Individual> firstGeneration(MatchingProblem problem, MatchingBudget budget, Random random) {
    List<Individual> population = new ArrayList<>();
    for (int i = 0; i < POPULATION && budget.spend(); i++) {
      int[] order = shuffled(problem.size(), random);
      population.add(new Individual(order, score(problem, order)));
    }
    return population;
  }

  // the elite as they are, then children until the population is full or the budget runs out
  private static List<Individual> nextGeneration(MatchingProblem problem, List<Individual> population,
      MatchingBudget budget, Random random) {
    List<Individual> next = new ArrayList<>(population.stream()
        .sorted(Comparator.comparingDouble(Individual::score)).limit(ELITE).toList());
    while (next.size() < POPULATION && budget.spend()) {
      next.add(child(problem, population, random));
    }
    return next;
  }

  private static Individual child(MatchingProblem problem, List<Individual> population, Random random) {
    int[] order = orderCrossover(tournament(population, random).order(), tournament(population, random).order(),
        random);
    if (random.nextDouble() < MUTATION_RATE) {
      swap(order, random.nextInt(order.length), random.nextInt(order.length));
    }
    return new Individual(order, score(problem, order));
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
  private static Individual tournament(List<Individual> population, Random random) {
    Individual first = population.get(random.nextInt(population.size()));
    Individual second = population.get(random.nextInt(population.size()));
    return first.score() <= second.score() ? first : second;
  }

  // the lowest score, the first one on a tie
  private static Individual fittest(List<Individual> population) {
    Individual best = population.getFirst();
    for (Individual individual : population) {
      if (individual.score() < best.score()) {
        best = individual;
      }
    }
    return best;
  }

  private static void swap(int[] order, int first, int second) {
    int student = order[first];
    order[first] = order[second];
    order[second] = student;
  }

  private static int[] shuffled(int size, Random random) {
    int[] order = new int[size];
    Arrays.setAll(order, i -> i);
    for (int i = size - 1; i > 0; i--) {
      swap(order, i, random.nextInt(i + 1));
    }
    return order;
  }

  private static double score(MatchingProblem problem, int[] order) {
    return SeparationRepair.penalizedCost(problem, new Grouping(problem.fill(order), problem.getGroupCount()));
  }

}
