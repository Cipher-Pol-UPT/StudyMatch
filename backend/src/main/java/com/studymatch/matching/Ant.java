package com.studymatch.matching;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

// builds one grouping: each student goes to a group picked at random, likelier where the pheromone is high and the
// cost low; keeps nothing between builds, so ants on different cores can share it
final class Ant {

  private final MatchingProblem problem;
  private final Pheromone pheromone;
  private final AntColonyMatching.Settings settings;

  Ant(MatchingProblem problem, Pheromone pheromone, AntColonyMatching.Settings settings) {
    this.problem = problem;
    this.pheromone = pheromone;
    this.settings = settings;
  }

  // null when a student ends up with nowhere to go
  int[] build(Random random) {
    int groups = problem.getGroupCount();
    int[] capacity = problem.groupSizes();
    List<GroupDraft> drafts = new ArrayList<>();
    for (int group = 0; group < groups; group++) {
      drafts.add(new GroupDraft(problem));
    }
    int[] groupOf = new int[problem.size()];
    double[] weights = new double[groups];

    for (int student : problem.placingOrder(random)) {
      double total = 0;
      for (int group = 0; group < groups; group++) {
        GroupDraft draft = drafts.get(group);
        boolean open = draft.size() < capacity[group] && !draft.conflictsWith(student);
        weights[group] = open ? weight(draft, student) : 0;
        total += weights[group];
      }
      if (total == 0) {
        return null;
      }
      int chosen = pick(weights, total, random);
      drafts.get(chosen).add(student);
      groupOf[student] = chosen;
    }
    return groupOf;
  }

  // pheromone^a * heuristic^b, where the heuristic is e^-(cost increase), fairness included
  private double weight(GroupDraft draft, int student) {
    double increase = draft.costWith(student) - draft.cost();
    if (settings.fairnessWeight() > 0) {
      increase += settings.fairnessWeight() * draft.fairnessWith(student);
    }
    return Math.pow(pheromone.attraction(student, draft.students()), settings.pheromonePower())
        * Math.pow(Math.exp(-increase), settings.heuristicPower());
  }

  // roulette wheel: each group's chance is its share of the total weight
  private static int pick(double[] weights, double total, Random random) {
    double spin = random.nextDouble() * total;
    int last = -1;
    for (int group = 0; group < weights.length; group++) {
      if (weights[group] == 0) {
        continue;
      }
      last = group;
      spin -= weights[group];
      if (spin < 0) {
        return group;
      }
    }
    return last;
  }

}
