package com.studymatch.matching;

import static com.studymatch.matching.MatchingProblem.AGREEABLENESS;
import static com.studymatch.matching.MatchingProblem.CONSCIENTIOUSNESS;
import static com.studymatch.matching.MatchingProblem.EMOTIONAL_STABILITY;
import static com.studymatch.matching.MatchingProblem.EXTRAVERSION;
import static com.studymatch.matching.MatchingProblem.TRAITS;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.ToDoubleFunction;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import com.studymatch.matching.MatchingProblem.Member;

// ranks every student against the whole edition: traits and level as z-scores, flags by rank since scores tie a lot
public final class EditionScores {

  static final double LOW_SHARE = 0.2;
  static final double HIGH_CONSCIENTIOUSNESS_SHARE = 0.2;
  static final double HIGH_EXTRAVERSION_SHARE = 0.33;

  private EditionScores() {
  }

  public static List<Member> members(List<Candidate> candidates) {
    List<Integer> tested = IntStream.range(0, candidates.size()).filter(i -> candidates.get(i).hasTest())
        .boxed().toList();
    double[][] traits = new double[candidates.size()][TRAITS];
    for (int trait = 0; trait < TRAITS; trait++) {
      int index = trait;
      double[] scores = zScores(candidates, tested, candidate -> candidate.traits().get(index));
      for (int i : tested) {
        traits[i][trait] = scores[i];
      }
    }
    double[] levels = levels(candidates);
    int[] flags = flags(candidates, tested);

    List<Member> members = new ArrayList<>();
    for (int i = 0; i < candidates.size(); i++) {
      members.add(new Member(traits[i], levels[i], candidates.get(i).languages(), flags[i]));
    }
    return members;
  }

  // NaN without grades, the cost leaves them out of the level terms (GR6)
  private static double[] levels(List<Candidate> candidates) {
    List<Integer> graded = IntStream.range(0, candidates.size()).filter(i -> candidates.get(i).level() != null)
        .boxed().toList();
    double[] scores = zScores(candidates, graded, Candidate::level);
    double[] levels = new double[candidates.size()];
    for (int i = 0; i < levels.length; i++) {
      levels[i] = candidates.get(i).level() == null ? Double.NaN : scores[i];
    }
    return levels;
  }

  // filled in for the given students only, the rest stay at 0 (the average)
  private static double[] zScores(List<Candidate> candidates, List<Integer> students,
      ToDoubleFunction<Candidate> value) {
    double[] scores = new double[candidates.size()];
    double mean = students.stream().mapToDouble(i -> value.applyAsDouble(candidates.get(i))).average().orElse(0);
    double variance = students.stream()
        .mapToDouble(i -> Math.pow(value.applyAsDouble(candidates.get(i)) - mean, 2)).average().orElse(0);
    double spread = Math.sqrt(variance);
    for (int i : students) {
      scores[i] = spread == 0 ? 0 : (value.applyAsDouble(candidates.get(i)) - mean) / spread;
    }
    return scores;
  }

  private static int[] flags(List<Candidate> candidates, List<Integer> tested) {
    int[] flags = new int[candidates.size()];
    mark(flags, bottom(candidates, tested, AGREEABLENESS, LOW_SHARE), MatchingProblem.LOW_AGREEABLENESS);
    mark(flags, bottom(candidates, tested, EMOTIONAL_STABILITY, LOW_SHARE),
        MatchingProblem.LOW_EMOTIONAL_STABILITY);
    mark(flags, bottom(candidates, tested, CONSCIENTIOUSNESS, LOW_SHARE), MatchingProblem.LOW_CONSCIENTIOUSNESS);
    mark(flags, top(candidates, tested, CONSCIENTIOUSNESS, HIGH_CONSCIENTIOUSNESS_SHARE),
        MatchingProblem.HIGH_CONSCIENTIOUSNESS);
    mark(flags, top(candidates, tested, EXTRAVERSION, HIGH_EXTRAVERSION_SHARE), MatchingProblem.HIGH_EXTRAVERSION);
    return flags;
  }

  private static void mark(int[] flags, List<Integer> students, int flag) {
    for (int i : students) {
      flags[i] |= flag;
    }
  }

  // the lowest share of the tested students on one trait, ties in list order so the same edition gets the same flags
  private static List<Integer> bottom(List<Candidate> candidates, List<Integer> tested, int trait, double share) {
    return ranked(candidates, tested, trait).limit(count(tested.size(), share)).toList();
  }

  private static List<Integer> top(List<Candidate> candidates, List<Integer> tested, int trait, double share) {
    List<Integer> ranked = ranked(candidates, tested, trait).toList();
    return ranked.reversed().stream().limit(count(tested.size(), share)).toList();
  }

  private static Stream<Integer> ranked(List<Candidate> candidates, List<Integer> tested,
      int trait) {
    return tested.stream().sorted(Comparator.<Integer>comparingDouble(i -> candidates.get(i).traits().get(trait))
        .thenComparing(i -> i));
  }

  private static long count(int students, double share) {
    return Math.round(students * share);
  }

}
