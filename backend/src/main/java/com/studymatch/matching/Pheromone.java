package com.studymatch.matching;

import java.util.Arrays;
import java.util.List;

// what the colony has learned: for every pair of students, how well they did together in good groupings
final class Pheromone {

  // MAX-MIN ceiling, so one early lucky grouping can't take over; the floor keeps every pair possible
  static final double MAX = 1;

  private final double[][] levels;
  private final double floor;

  Pheromone(int students, double floor) {
    this.levels = new double[students][students];
    this.floor = floor;
    reset();
  }

  // back to the ceiling everywhere, so the colony explores again
  void reset() {
    for (double[] row : levels) {
      Arrays.fill(row, MAX);
    }
  }

  // the mean pheromone between the student and the group's members, 1 for an empty group
  double attraction(int student, List<Integer> members) {
    if (members.isEmpty()) {
      return 1;
    }
    double sum = 0;
    for (int member : members) {
      sum += levels[student][member];
    }
    return sum / members.size();
  }

  // everything evaporates a bit, then the pairs the grouping put together get more, kept between floor and ceiling
  void reinforce(Grouping grouping, double cost, double evaporation) {
    double deposit = 1 / (1 + cost);
    for (int first = 0; first < levels.length; first++) {
      for (int second = 0; second < levels.length; second++) {
        double value = levels[first][second] * (1 - evaporation);
        if (first != second && grouping.groupOf(first) == grouping.groupOf(second)) {
          value += deposit;
        }
        levels[first][second] = Math.clamp(value, floor, MAX);
      }
    }
  }

}
