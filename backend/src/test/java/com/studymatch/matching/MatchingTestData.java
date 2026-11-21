package com.studymatch.matching;

import static com.studymatch.matching.MatchingProblem.AGREEABLENESS;
import static com.studymatch.matching.MatchingProblem.CONSCIENTIOUSNESS;
import static com.studymatch.matching.MatchingProblem.EMOTIONAL_STABILITY;
import static com.studymatch.matching.MatchingProblem.EXTRAVERSION;
import static com.studymatch.matching.MatchingProblem.HIGH_CONSCIENTIOUSNESS;
import static com.studymatch.matching.MatchingProblem.HIGH_EXTRAVERSION;
import static com.studymatch.matching.MatchingProblem.LOW_AGREEABLENESS;
import static com.studymatch.matching.MatchingProblem.LOW_CONSCIENTIOUSNESS;
import static com.studymatch.matching.MatchingProblem.LOW_EMOTIONAL_STABILITY;
import static com.studymatch.matching.MatchingProblem.TRAITS;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.studymatch.matching.MatchingProblem.Member;

// random pools from a seed, the same seed always gives the same pool
public final class MatchingTestData {

  // z-scores of the bottom 20% and the top 33% of a normal distribution
  private static final double BOTTOM_20 = -0.84;
  private static final double TOP_20 = 0.84;
  private static final double TOP_33 = 0.44;

  private static final int PORTUGUESE = 1;
  private static final int ENGLISH = 1 << 1;

  private MatchingTestData() {
  }

  public static MatchingProblem randomProblem(int students, double separationShare, long seed, int minSize,
      int optimalSize, int maxSize) {
    Random random = new Random(seed);
    List<Member> members = new ArrayList<>();
    for (int i = 0; i < students; i++) {
      members.add(randomMember(random));
    }

    List<int[]> separations = new ArrayList<>();
    for (int first = 0; first < students; first++) {
      for (int second = first + 1; second < students; second++) {
        if (random.nextDouble() < separationShare) {
          separations.add(new int[] { first, second });
        }
      }
    }
    return new MatchingProblem(members, separations, minSize, optimalSize, maxSize);
  }

  private static Member randomMember(Random random) {
    double[] traits = new double[TRAITS];
    for (int trait = 0; trait < TRAITS; trait++) {
      traits[trait] = random.nextGaussian();
    }
    // a few students without grades yet
    double level = random.nextDouble() < 0.1 ? Double.NaN : random.nextGaussian();
    int languages = PORTUGUESE | (random.nextBoolean() ? ENGLISH : 0);

    int flags = 0;
    if (traits[AGREEABLENESS] < BOTTOM_20) {
      flags |= LOW_AGREEABLENESS;
    }
    if (traits[EMOTIONAL_STABILITY] < BOTTOM_20) {
      flags |= LOW_EMOTIONAL_STABILITY;
    }
    if (traits[CONSCIENTIOUSNESS] < BOTTOM_20) {
      flags |= LOW_CONSCIENTIOUSNESS;
    }
    if (traits[CONSCIENTIOUSNESS] > TOP_20) {
      flags |= HIGH_CONSCIENTIOUSNESS;
    }
    if (traits[EXTRAVERSION] > TOP_33) {
      flags |= HIGH_EXTRAVERSION;
    }
    return new Member(traits, level, languages, flags);
  }

}
