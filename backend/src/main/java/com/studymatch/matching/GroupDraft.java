package com.studymatch.matching;

import static com.studymatch.matching.MatchingCost.AGREEABLENESS_WEIGHT;
import static com.studymatch.matching.MatchingCost.CONSCIENTIOUSNESS_WEIGHT;
import static com.studymatch.matching.MatchingCost.EMOTIONAL_STABILITY_MAX_RANGE;
import static com.studymatch.matching.MatchingCost.EMOTIONAL_STABILITY_WEIGHT;
import static com.studymatch.matching.MatchingCost.EXTRAVERSION_WEIGHT;
import static com.studymatch.matching.MatchingCost.LANGUAGE_WEIGHT;
import static com.studymatch.matching.MatchingCost.LEVEL_MIN_SPREAD;
import static com.studymatch.matching.MatchingCost.LEVEL_WEIGHT;
import static com.studymatch.matching.MatchingCost.SIZE_WEIGHT;
import static com.studymatch.matching.MatchingProblem.EMOTIONAL_STABILITY;
import static com.studymatch.matching.MatchingProblem.HIGH_CONSCIENTIOUSNESS;
import static com.studymatch.matching.MatchingProblem.HIGH_EXTRAVERSION;
import static com.studymatch.matching.MatchingProblem.LOW_AGREEABLENESS;
import static com.studymatch.matching.MatchingProblem.LOW_CONSCIENTIOUSNESS;
import static com.studymatch.matching.MatchingProblem.LOW_EMOTIONAL_STABILITY;

import java.util.ArrayList;
import java.util.List;

import com.studymatch.matching.MatchingProblem.Member;

// a group while an ant builds it: it only grows, so running counts give MatchingCost.groupCost without a pass
final class GroupDraft {

  private final MatchingProblem problem;
  private final List<Integer> students = new ArrayList<>();
  private final long[] studentBits;
  private final double[] traitSums = new double[MatchingProblem.TRAITS];
  private int lowAgreeableness;
  private int lowEmotionalStability;
  private int lowConscientiousness;
  private int highConscientiousness;
  private int highExtraversion;
  private int languages = ~0;
  private double minStability = Double.POSITIVE_INFINITY;
  private double maxStability = Double.NEGATIVE_INFINITY;
  private int levels;
  private double levelSum;
  private double levelSquares;

  GroupDraft(MatchingProblem problem) {
    this.problem = problem;
    this.studentBits = new long[Bits.words(problem.size())];
  }

  // someone in the group is separated from the student
  boolean conflictsWith(int student) {
    return Bits.overlap(studentBits, problem.separationBits(student), -1);
  }

  List<Integer> students() {
    return students;
  }

  int size() {
    return students.size();
  }

  void add(int student) {
    Member member = problem.member(student);
    students.add(student);
    Bits.set(studentBits, student);
    for (int trait = 0; trait < MatchingProblem.TRAITS; trait++) {
      traitSums[trait] += member.traits()[trait];
    }
    lowAgreeableness += member.has(LOW_AGREEABLENESS) ? 1 : 0;
    lowEmotionalStability += member.has(LOW_EMOTIONAL_STABILITY) ? 1 : 0;
    lowConscientiousness += member.has(LOW_CONSCIENTIOUSNESS) ? 1 : 0;
    highConscientiousness += member.has(HIGH_CONSCIENTIOUSNESS) ? 1 : 0;
    highExtraversion += member.has(HIGH_EXTRAVERSION) ? 1 : 0;
    languages &= member.languages();
    minStability = Math.min(minStability, member.traits()[EMOTIONAL_STABILITY]);
    maxStability = Math.max(maxStability, member.traits()[EMOTIONAL_STABILITY]);
    if (member.hasLevel()) {
      levels++;
      levelSum += member.level();
      levelSquares += member.level() * member.level();
    }
  }

  double cost() {
    return cost(size(), lowAgreeableness, lowEmotionalStability, lowConscientiousness > 0 && highConscientiousness > 0,
        highExtraversion, languages, maxStability - minStability, levels, levelSum, levelSquares);
  }

  // what cost() would be after add(student), without adding them
  double costWith(int student) {
    Member member = problem.member(student);
    double stability = member.traits()[EMOTIONAL_STABILITY];
    boolean leveled = member.hasLevel();
    return cost(size() + 1,
        lowAgreeableness + (member.has(LOW_AGREEABLENESS) ? 1 : 0),
        lowEmotionalStability + (member.has(LOW_EMOTIONAL_STABILITY) ? 1 : 0),
        (lowConscientiousness > 0 || member.has(LOW_CONSCIENTIOUSNESS))
            && (highConscientiousness > 0 || member.has(HIGH_CONSCIENTIOUSNESS)),
        highExtraversion + (member.has(HIGH_EXTRAVERSION) ? 1 : 0),
        languages & member.languages(),
        Math.max(maxStability, stability) - Math.min(minStability, stability),
        levels + (leveled ? 1 : 0),
        levelSum + (leveled ? member.level() : 0),
        levelSquares + (leveled ? member.level() * member.level() : 0));
  }

  // how much the fairness terms would grow if the student joined: each group's share of the variance of the
  // group averages is (its average - the pool's average)^2 / groups, weighted like in MatchingCost
  double fairnessWith(int student) {
    Member member = problem.member(student);
    double change = 0;
    for (int trait = 0; trait < MatchingProblem.TRAITS; trait++) {
      double before = size() == 0 ? 0 : square(traitSums[trait] / size() - problem.traitAverage(trait));
      double after = square((traitSums[trait] + member.traits()[trait]) / (size() + 1)
          - problem.traitAverage(trait));
      change += MatchingCost.FAIRNESS_WEIGHTS[trait] * (after - before);
    }
    if (member.hasLevel()) {
      double before = levels == 0 ? 0 : square(levelSum / levels - problem.levelAverage());
      double after = square((levelSum + member.level()) / (levels + 1) - problem.levelAverage());
      change += LEVEL_WEIGHT * (after - before);
    }
    return change / problem.getGroupCount();
  }

  // the same rules as MatchingCost.groupCost, from the counts
  private double cost(int size, int lowA, int lowEs, boolean conscientiousnessGap, int extraverts, int common,
      double stabilityRange, int levelCount, double sum, double squares) {
    // an empty group has no extravert and isn't at the optimal size
    if (size == 0) {
      return EXTRAVERSION_WEIGHT + SIZE_WEIGHT;
    }
    double total = AGREEABLENESS_WEIGHT * square(Math.max(0, lowA - 1));
    total += EMOTIONAL_STABILITY_WEIGHT
        * (square(Math.max(0, lowEs - 1)) + Math.max(0, stabilityRange - EMOTIONAL_STABILITY_MAX_RANGE));
    total += conscientiousnessGap ? CONSCIENTIOUSNESS_WEIGHT : 0;
    total += extraverts == 0 || extraverts >= 3 ? EXTRAVERSION_WEIGHT : 0;
    total += common == 0 ? LANGUAGE_WEIGHT : 0;
    total += size == problem.getOptimalSize() ? 0 : SIZE_WEIGHT;
    if (levelCount > 1) {
      double mean = sum / levelCount;
      double spread = Math.sqrt(Math.max(0, squares / levelCount - mean * mean));
      total += LEVEL_WEIGHT * Math.max(0, LEVEL_MIN_SPREAD - spread);
    }
    return total;
  }

  private static double square(double value) {
    return value * value;
  }

}
