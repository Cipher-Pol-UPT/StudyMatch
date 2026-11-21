package com.studymatch.matching;

import static com.studymatch.matching.MatchingProblem.TRAITS;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import com.studymatch.matching.MatchingProblem.Member;

// the cost of a grouping that changes by swaps and moves: only the two groups touched are recalculated
public final class TrackedCost {

  private final MatchingProblem problem;
  private final int[] groupOf;
  private final List<List<Integer>> members = new ArrayList<>();
  // the same members as bits, for the separation check
  private final long[][] memberBits;
  private final double[] penalties;
  private final double[][] traitMeans;
  private final double[][] traitSpreads;
  // NaN for a group where nobody has grades yet
  private final double[] levelMeans;
  private double total;

  public TrackedCost(MatchingProblem problem, Grouping grouping) {
    this.problem = problem;
    int groups = grouping.groupCount();
    this.groupOf = new int[problem.size()];
    this.penalties = new double[groups];
    this.traitMeans = new double[TRAITS][groups];
    this.traitSpreads = new double[TRAITS][groups];
    this.levelMeans = new double[groups];
    this.memberBits = new long[groups][Bits.words(problem.size())];
    for (int group = 0; group < groups; group++) {
      members.add(new ArrayList<>());
    }
    for (int student = 0; student < problem.size(); student++) {
      groupOf[student] = grouping.groupOf(student);
      members.get(groupOf[student]).add(student);
      Bits.set(memberBits[groupOf[student]], student);
    }
    for (int group = 0; group < groups; group++) {
      refresh(group);
    }
    total = sum();
  }

  public double total() {
    return total;
  }

  public int groupOf(int student) {
    return groupOf[student];
  }

  public int size(int group) {
    return members.get(group).size();
  }

  public List<Integer> members(int group) {
    return List.copyOf(members.get(group));
  }

  // the group's own penalties, without the fairness terms that compare it to the others
  public double penalty(int group) {
    return penalties[group];
  }

  public int groupCount() {
    return penalties.length;
  }

  // a member of a group picked with odds by its penalty, so the groups paying the most get worked on first
  public int memberOfCostlyGroup(Random random) {
    double sum = 0;
    for (double penalty : penalties) {
      sum += penalty;
    }
    int chosen = random.nextInt(groupCount());
    if (sum > 0) {
      double spin = random.nextDouble() * sum;
      for (int group = 0; group < groupCount() && spin >= 0; group++) {
        spin -= penalties[group];
        chosen = group;
      }
    }
    List<Integer> groupMembers = members.get(chosen);
    return groupMembers.get(random.nextInt(groupMembers.size()));
  }

  // the same check as MatchingCost.canJoin, as bits: one AND per 64 students instead of a loop over the members
  public boolean canJoin(int student, int group, int leaving) {
    return !Bits.overlap(memberBits[group], problem.separationBits(student), leaving);
  }

  // returns the new total; swapping the same two again undoes it
  public double swap(int first, int second) {
    int firstGroup = groupOf[first];
    int secondGroup = groupOf[second];
    if (firstGroup == secondGroup) {
      return total;
    }
    members.get(firstGroup).remove(Integer.valueOf(first));
    members.get(secondGroup).remove(Integer.valueOf(second));
    members.get(firstGroup).add(second);
    members.get(secondGroup).add(first);
    Bits.clear(memberBits[firstGroup], first);
    Bits.clear(memberBits[secondGroup], second);
    Bits.set(memberBits[firstGroup], second);
    Bits.set(memberBits[secondGroup], first);
    groupOf[first] = secondGroup;
    groupOf[second] = firstGroup;
    return update(firstGroup, secondGroup);
  }

  // returns the new total; moving back undoes it
  public double move(int student, int group) {
    int from = groupOf[student];
    if (from == group) {
      return total;
    }
    members.get(from).remove(Integer.valueOf(student));
    members.get(group).add(student);
    Bits.clear(memberBits[from], student);
    Bits.set(memberBits[group], student);
    groupOf[student] = group;
    return update(from, group);
  }

  public Grouping grouping() {
    return new Grouping(groupOf, penalties.length);
  }

  private double update(int first, int second) {
    refresh(first);
    refresh(second);
    total = sum();
    return total;
  }

  // the numbers MatchingCost.cost works out per group, for one group
  private void refresh(int group) {
    List<Member> groupMembers = members.get(group).stream().map(problem::member).toList();
    if (groupMembers.isEmpty()) {
      penalties[group] = 0;
      for (int trait = 0; trait < TRAITS; trait++) {
        traitMeans[trait][group] = 0;
        traitSpreads[trait][group] = 0;
      }
      levelMeans[group] = Double.NaN;
      return;
    }
    penalties[group] = MatchingCost.groupCost(problem, groupMembers);
    for (int trait = 0; trait < TRAITS; trait++) {
      double[] values = MatchingCost.traitValues(groupMembers, trait);
      traitMeans[trait][group] = MatchingCost.mean(values);
      traitSpreads[trait][group] = MatchingCost.standardDeviation(values);
    }
    double[] levels = MatchingCost.levels(groupMembers);
    levelMeans[group] = levels.length == 0 ? Double.NaN : MatchingCost.mean(levels);
  }

  // the group penalties plus the fairness terms, from the numbers kept per group
  private double sum() {
    double sum = 0;
    for (double penalty : penalties) {
      sum += penalty;
    }
    for (int trait = 0; trait < TRAITS; trait++) {
      sum += MatchingCost.FAIRNESS_WEIGHTS[trait]
          * (MatchingCost.variance(traitMeans[trait]) + MatchingCost.variance(traitSpreads[trait]));
    }
    double[] levels = Arrays.stream(levelMeans).filter(level -> !Double.isNaN(level)).toArray();
    return sum + MatchingCost.LEVEL_WEIGHT * MatchingCost.variance(levels);
  }

}
