package com.studymatch.matching;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

// FTTA's three training phases on random keys: each player is a number per student, sorting them gives the groups
public final class FttaMatching implements MatchingAlgorithm {

  static final int PLAYERS = 30;
  static final int TRAINING_GROUPS = 5;
  // individual training: the share of a player's numbers nudged, and by how much
  static final double TWEAK_SHARE = 0.1;
  static final double TWEAK_SIZE = 0.05;

  private final GreedyMatching greedy = new GreedyMatching();

  @Override
  public Grouping solve(MatchingProblem problem, MatchingBudget budget, Random random) {
    Squad squad = new Squad(problem, budget);
    for (int player = 0; player < PLAYERS; player++) {
      if (!squad.join(RandomKeys.random(problem.size(), random))) {
        break;
      }
    }
    if (squad.size() == 0) {
      return greedy.solve(problem, budget, random);
    }

    while (!budget.isExhausted()) {
      collectiveTraining(squad, random);
      groupTraining(squad, random);
      individualTraining(squad, random);
    }

    Grouping best = RandomKeys.finish(problem, squad.player(squad.star()), random);
    return best != null ? best : greedy.solve(problem, budget, random);
  }

  // everyone learns from the star: a random part of the way towards the best player
  private static void collectiveTraining(Squad squad, Random random) {
    int star = squad.star();
    for (int player = 0; player < squad.size(); player++) {
      if (player != star && !squad.train(player, towards(squad.player(player), squad.player(star), random))) {
        return;
      }
    }
  }

  // small random training groups, each follows its own leader, so different ideas survive
  private static void groupTraining(Squad squad, Random random) {
    List<Integer> order = new ArrayList<>();
    for (int player = 0; player < squad.size(); player++) {
      order.add(player);
    }
    Collections.shuffle(order, random);
    int groupSize = Math.ceilDiv(order.size(), TRAINING_GROUPS);

    for (int start = 0; start < order.size(); start += groupSize) {
      List<Integer> group = order.subList(start, Math.min(start + groupSize, order.size()));
      int leader = group.stream().min(Comparator.comparingDouble(squad::score)).orElseThrow();
      for (int player : group) {
        if (player != leader && !squad.train(player, towards(squad.player(player), squad.player(leader), random))) {
          return;
        }
      }
    }
  }

  // fine-tuning: each player nudges a few of its own numbers
  private static void individualTraining(Squad squad, Random random) {
    for (int player = 0; player < squad.size(); player++) {
      double[] keys = squad.player(player).clone();
      for (int student = 0; student < keys.length; student++) {
        if (random.nextDouble() < TWEAK_SHARE) {
          keys[student] = Math.clamp(keys[student] + random.nextGaussian() * TWEAK_SIZE, 0, 1);
        }
      }
      if (!squad.train(player, keys)) {
        return;
      }
    }
  }

  private static double[] towards(double[] keys, double[] target, Random random) {
    double[] moved = new double[keys.length];
    for (int student = 0; student < keys.length; student++) {
      moved[student] = keys[student] + random.nextDouble() * (target[student] - keys[student]);
    }
    return moved;
  }

  // the players and their scores; a new position is only kept if it scores better
  private static final class Squad {

    private final MatchingProblem problem;
    private final MatchingBudget budget;
    private final List<double[]> players = new ArrayList<>();
    private final List<Double> scores = new ArrayList<>();

    Squad(MatchingProblem problem, MatchingBudget budget) {
      this.problem = problem;
      this.budget = budget;
    }

    // false once the budget is gone
    boolean join(double[] keys) {
      if (!budget.spend()) {
        return false;
      }
      players.add(keys);
      scores.add(evaluate(keys));
      return true;
    }

    // false once the budget is gone
    boolean train(int player, double[] keys) {
      if (!budget.spend()) {
        return false;
      }
      double score = evaluate(keys);
      if (score < scores.get(player)) {
        players.set(player, keys);
        scores.set(player, score);
      }
      return true;
    }

    int size() {
      return players.size();
    }

    double[] player(int player) {
      return players.get(player);
    }

    double score(int player) {
      return scores.get(player);
    }

    int star() {
      int star = 0;
      for (int player = 1; player < size(); player++) {
        if (scores.get(player) < scores.get(star)) {
          star = player;
        }
      }
      return star;
    }

    private double evaluate(double[] keys) {
      return RandomKeys.score(problem, keys);
    }

  }

}
