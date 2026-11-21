package com.studymatch.matching;

import static com.studymatch.matching.MatchingProblem.AGREEABLENESS;
import static com.studymatch.matching.MatchingProblem.EMOTIONAL_STABILITY;
import static com.studymatch.matching.MatchingProblem.HIGH_CONSCIENTIOUSNESS;
import static com.studymatch.matching.MatchingProblem.HIGH_EXTRAVERSION;
import static com.studymatch.matching.MatchingProblem.LOW_AGREEABLENESS;
import static com.studymatch.matching.MatchingProblem.LOW_CONSCIENTIOUSNESS;
import static com.studymatch.matching.MatchingProblem.LOW_EMOTIONAL_STABILITY;
import static com.studymatch.matching.MatchingProblem.TRAITS;

import java.util.ArrayList;
import java.util.List;

import com.studymatch.matching.MatchingProblem.Member;

// how good a grouping is (lower is better) and whether it's allowed at all
public final class MatchingCost {

  public static final double AGREEABLENESS_WEIGHT = 3;
  public static final double EMOTIONAL_STABILITY_WEIGHT = 3;
  public static final double CONSCIENTIOUSNESS_WEIGHT = 2;
  public static final double EXTRAVERSION_WEIGHT = 1.5;
  // openness, conscientiousness, extraversion, agreeableness, emotional stability
  static final double[] FAIRNESS_WEIGHTS = { 0.5, 1, 1, 1, 1 };
  public static final double LEVEL_WEIGHT = 2.5;
  public static final double LANGUAGE_WEIGHT = 10;
  public static final double SIZE_WEIGHT = 0.5;

  // in z-scores: a bigger emotional stability gap in one group is penalised
  public static final double EMOTIONAL_STABILITY_MAX_RANGE = 2.0;

  private MatchingCost() {
  }

  // the hard rules: sizes within the range and no separated pair together
  public static boolean isValid(MatchingProblem problem, Grouping grouping) {
    for (int group = 0; group < grouping.groupCount(); group++) {
      if (grouping.size(group) < problem.getMinSize() || grouping.size(group) > problem.getMaxSize()) {
        return false;
      }
    }
    for (int[] pair : problem.getSeparations()) {
      if (grouping.groupOf(pair[0]) == grouping.groupOf(pair[1])) {
        return false;
      }
    }
    return true;
  }

  // nobody in the group is separated from the student; leaving is someone swapping out of it (-1 for nobody)
  public static boolean canJoin(MatchingProblem problem, Grouping grouping, int student, int group, int leaving) {
    for (int member : grouping.members(group)) {
      if (member != leaving && problem.separated(student, member)) {
        return false;
      }
    }
    return true;
  }

  public static double cost(MatchingProblem problem, Grouping grouping) {
    int groups = grouping.groupCount();
    double[][] traitMeans = new double[TRAITS][groups];
    double[][] traitSpreads = new double[TRAITS][groups];
    List<Double> levelMeans = new ArrayList<>();
    double total = 0;

    for (int group = 0; group < groups; group++) {
      List<Member> members = grouping.members(group).stream().map(problem::member).toList();
      if (members.isEmpty()) {
        continue;
      }
      total += groupCost(problem, members);

      for (int trait = 0; trait < TRAITS; trait++) {
        double[] values = traitValues(members, trait);
        traitMeans[trait][group] = mean(values);
        traitSpreads[trait][group] = standardDeviation(values);
      }
      // grades only count here, as even group averages; without grades a student is left out (GR6)
      double[] levels = levels(members);
      if (levels.length > 0) {
        levelMeans.add(mean(levels));
      }
    }

    // every group should look like the others (anticlustering)
    for (int trait = 0; trait < TRAITS; trait++) {
      total += FAIRNESS_WEIGHTS[trait] * (variance(traitMeans[trait]) + variance(traitSpreads[trait]));
    }
    total += LEVEL_WEIGHT * variance(levelMeans.stream().mapToDouble(Double::doubleValue).toArray());
    return total;
  }

  // what one group costs on its own, without comparing it to the others; works on a half-built group too
  public static double groupCost(MatchingProblem problem, List<Member> members) {
    double total = personalityPenalty(members);
    total += LANGUAGE_WEIGHT * (sharesLanguage(members) ? 0 : 1);
    total += SIZE_WEIGHT * (members.size() == problem.getOptimalSize() ? 0 : 1);
    return total;
  }

  private static double personalityPenalty(List<Member> members) {
    int lowAgreeableness = count(members, LOW_AGREEABLENESS);
    int lowEmotionalStability = count(members, LOW_EMOTIONAL_STABILITY);
    int highExtraversion = count(members, HIGH_EXTRAVERSION);
    double[] emotionalStability = traitValues(members, EMOTIONAL_STABILITY);
    double range = max(emotionalStability) - min(emotionalStability);

    double penalty = AGREEABLENESS_WEIGHT * square(Math.max(0, lowAgreeableness - 1));
    penalty += EMOTIONAL_STABILITY_WEIGHT * (square(Math.max(0, lowEmotionalStability - 1))
        + Math.max(0, range - EMOTIONAL_STABILITY_MAX_RANGE));
    if (count(members, HIGH_CONSCIENTIOUSNESS) > 0 && count(members, LOW_CONSCIENTIOUSNESS) > 0) {
      penalty += CONSCIENTIOUSNESS_WEIGHT;
    }
    if (highExtraversion == 0 || highExtraversion >= 3) {
      penalty += EXTRAVERSION_WEIGHT;
    }
    return penalty;
  }

  // GR4: one language everyone in the group speaks
  private static boolean sharesLanguage(List<Member> members) {
    int common = ~0;
    for (Member member : members) {
      common &= member.languages();
    }
    return common != 0;
  }

  private static int count(List<Member> members, int flag) {
    return (int) members.stream().filter(member -> member.has(flag)).count();
  }

  static double[] traitValues(List<Member> members, int trait) {
    return members.stream().mapToDouble(member -> member.traits()[trait]).toArray();
  }

  static double[] levels(List<Member> members) {
    return members.stream().filter(Member::hasLevel).mapToDouble(Member::level).toArray();
  }

  private static double square(double value) {
    return value * value;
  }

  static double mean(double[] values) {
    double sum = 0;
    for (double value : values) {
      sum += value;
    }
    return values.length == 0 ? 0 : sum / values.length;
  }

  static double variance(double[] values) {
    double mean = mean(values);
    double sum = 0;
    for (double value : values) {
      sum += square(value - mean);
    }
    return values.length == 0 ? 0 : sum / values.length;
  }

  static double standardDeviation(double[] values) {
    return Math.sqrt(variance(values));
  }

  private static double max(double[] values) {
    double max = Double.NEGATIVE_INFINITY;
    for (double value : values) {
      max = Math.max(max, value);
    }
    return max;
  }

  private static double min(double[] values) {
    double min = Double.POSITIVE_INFINITY;
    for (double value : values) {
      min = Math.min(min, value);
    }
    return min;
  }

}
