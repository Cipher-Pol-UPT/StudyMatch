package com.studymatch.matching;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

// one pool of students for group matching, as plain arrays: a student is just their index
public final class MatchingProblem {

  public static final int TRAITS = 5;
  public static final int OPENNESS = 0;
  public static final int CONSCIENTIOUSNESS = 1;
  public static final int EXTRAVERSION = 2;
  public static final int AGREEABLENESS = 3;
  public static final int EMOTIONAL_STABILITY = 4;

  // flags, ranked against the whole edition
  public static final int LOW_AGREEABLENESS = 1;
  public static final int LOW_EMOTIONAL_STABILITY = 1 << 1;
  public static final int LOW_CONSCIENTIOUSNESS = 1 << 2;
  public static final int HIGH_CONSCIENTIOUSNESS = 1 << 3;
  public static final int HIGH_EXTRAVERSION = 1 << 4;

  // traits and level are z-scores within the edition; level is NaN when there are no grades yet (GR6)
  public record Member(double[] traits, double level, int languages, int flags) {

    public boolean has(int flag) {
      return (flags & flag) != 0;
    }

    public boolean hasLevel() {
      return !Double.isNaN(level);
    }

  }

  private final List<Member> members;
  private final List<int[]> separations;
  // separated[a][b] is true when the teacher wants a and b apart
  private final boolean[][] separated;
  // the same as bits, one per student: the bitset version of the check
  private final long[][] separationBits;
  // the pool's averages, what every group should look like for the fairness terms
  private final double[] traitAverages = new double[TRAITS];
  private final double levelAverage;
  private final int minSize;
  private final int optimalSize;
  private final int maxSize;
  private final int groupCount;

  public MatchingProblem(List<Member> members, List<int[]> separations, int minSize, int optimalSize,
      int maxSize) {
    int groups = groupCount(members.size(), minSize, optimalSize, maxSize);
    if (groups < 1) {
      throw new IllegalArgumentException("no valid split for " + members.size() + " students");
    }
    this.members = List.copyOf(members);
    this.separations = List.copyOf(separations);
    this.separated = new boolean[members.size()][members.size()];
    this.separationBits = new long[members.size()][Bits.words(members.size())];
    for (int[] pair : separations) {
      separated[pair[0]][pair[1]] = true;
      separated[pair[1]][pair[0]] = true;
      Bits.set(separationBits[pair[0]], pair[1]);
      Bits.set(separationBits[pair[1]], pair[0]);
    }
    for (Member member : members) {
      for (int trait = 0; trait < TRAITS; trait++) {
        traitAverages[trait] += member.traits()[trait] / members.size();
      }
    }
    this.levelAverage = members.stream().filter(Member::hasLevel).mapToDouble(Member::level).average()
        .orElse(Double.NaN);
    this.minSize = minSize;
    this.optimalSize = optimalSize;
    this.maxSize = maxSize;
    this.groupCount = groups;
  }

  // most groups at the optimal size, then the fewest that aren't; -1 when no split fits the sizes
  public static int groupCount(int students, int minSize, int optimalSize, int maxSize) {
    int best = -1;
    int bestOptimal = -1;
    for (int groups = 1; groups * minSize <= students; groups++) {
      if (groups * maxSize < students) {
        continue;
      }
      int extra = students - groups * optimalSize;
      int step = extra > 0 ? maxSize - optimalSize : optimalSize - minSize;
      int offSize = extra == 0 ? 0 : Math.ceilDiv(Math.abs(extra), step);
      int optimal = groups - offSize;
      if (optimal > bestOptimal) {
        best = groups;
        bestOptimal = optimal;
      }
    }
    return best;
  }

  // the size of each group: the optimal one, with the leftover pushed into as few groups as possible
  public int[] groupSizes() {
    int[] sizes = new int[groupCount];
    Arrays.fill(sizes, optimalSize);
    int extra = size() - groupCount * optimalSize;
    int step = extra > 0 ? maxSize - optimalSize : optimalSize - minSize;
    for (int group = 0; extra != 0; group++) {
      int change = Math.min(step, Math.abs(extra)) * Integer.signum(extra);
      sizes[group] += change;
      extra -= change;
    }
    return sizes;
  }

  public int size() {
    return members.size();
  }

  public Member member(int student) {
    return members.get(student);
  }

  public List<int[]> getSeparations() {
    return separations;
  }

  public boolean separated(int first, int second) {
    return separated[first][second];
  }

  // everyone the student is separated from, as bits; don't change it
  long[] separationBits(int student) {
    return separationBits[student];
  }

  double traitAverage(int trait) {
    return traitAverages[trait];
  }

  // NaN when nobody has grades yet
  double levelAverage() {
    return levelAverage;
  }

  public int separationCount(int student) {
    int count = 0;
    for (boolean apart : separated[student]) {
      count += apart ? 1 : 0;
    }
    return count;
  }

  // most separations first, they're the hardest to place late; random among equals
  public List<Integer> placingOrder(Random random) {
    List<Integer> order = new ArrayList<>();
    for (int student = 0; student < size(); student++) {
      order.add(student);
    }
    Collections.shuffle(order, random);
    order.sort(Comparator.comparingInt((Integer student) -> separationCount(student)).reversed());
    return order;
  }

  // fills the groups in this order up to their planned sizes, so the sizes are always valid
  public int[] fill(int[] order) {
    int[] sizes = groupSizes();
    int[] groupOf = new int[order.length];
    int group = 0;
    int filled = 0;
    for (int student : order) {
      if (filled == sizes[group]) {
        group++;
        filled = 0;
      }
      groupOf[student] = group;
      filled++;
    }
    return groupOf;
  }

  public int getMinSize() {
    return minSize;
  }

  public int getOptimalSize() {
    return optimalSize;
  }

  public int getMaxSize() {
    return maxSize;
  }

  public int getGroupCount() {
    return groupCount;
  }

}
