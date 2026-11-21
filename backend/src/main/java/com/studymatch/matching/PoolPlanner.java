package com.studymatch.matching;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

// splits the class into the pools that are matched on their own: by class if asked, then by attendance tier (layer 1)
public final class PoolPlanner {

  // tier edges, highest first: 90% and up, 75 to 90, 50 to 75, below 50
  static final double[] TIER_EDGES = { 90, 75, 50 };

  private final List<Candidate> candidates;
  private final int minSize;
  private final int optimalSize;
  private final int maxSize;

  private PoolPlanner(List<Candidate> candidates, int minSize, int optimalSize, int maxSize) {
    this.candidates = candidates;
    this.minSize = minSize;
    this.optimalSize = optimalSize;
    this.maxSize = maxSize;
  }

  // every student ends up in exactly one pool, and every pool can be split within the sizes
  public static List<List<Integer>> plan(List<Candidate> candidates, int minSize, int optimalSize, int maxSize,
      boolean sameClass) {
    PoolPlanner planner = new PoolPlanner(candidates, minSize, optimalSize, maxSize);
    List<List<Integer>> pools = new ArrayList<>();
    for (List<Integer> section : planner.sections(sameClass)) {
      if (!planner.splits(section.size())) {
        throw new NoValidSplitException(section.size());
      }
      pools.addAll(planner.planSection(section));
    }
    return pools;
  }

  private List<List<Integer>> sections(boolean sameClass) {
    List<Integer> everyone = IntStream.range(0, candidates.size()).boxed().toList();
    if (!sameClass) {
      return List.of(everyone);
    }
    Map<String, List<Integer>> byClass = everyone.stream()
        .collect(Collectors.groupingBy(i -> candidates.get(i).classSection(), TreeMap::new, Collectors.toList()));
    return List.copyOf(byClass.values());
  }

  private List<List<Integer>> planSection(List<Integer> section) {
    List<Pool> pools = tiers(section.stream()
        .filter(i -> candidates.get(i).hasTest() && !candidates.get(i).isFiller()).toList());
    mergeSmallTiers(pools);
    section.stream().filter(i -> candidates.get(i).hasTest() && candidates.get(i).isFiller())
        .forEach(i -> place(pools, i));
    // without a test they're grouped among themselves, or fill the pools if they can't form groups alone
    List<Integer> untested = section.stream().filter(i -> !candidates.get(i).hasTest()).toList();
    if (splits(untested.size())) {
      pools.add(new Pool(new ArrayList<>(untested)));
    } else {
      untested.forEach(i -> place(pools, i));
    }
    if (pools.stream().anyMatch(pool -> !splits(pool.students.size()))) {
      return List.of(section);
    }
    return pools.stream().map(pool -> pool.students).toList();
  }

  // highest tier first, empty tiers left out
  private List<Pool> tiers(List<Integer> students) {
    List<Pool> tiers = new ArrayList<>();
    for (int tier = 0; tier <= TIER_EDGES.length; tier++) {
      int index = tier;
      List<Integer> members = students.stream().filter(i -> tierOf(candidates.get(i).attendance()) == index)
          .collect(Collectors.toCollection(ArrayList::new));
      if (!members.isEmpty()) {
        tiers.add(new Pool(members));
      }
    }
    return tiers;
  }

  static int tierOf(double attendance) {
    int tier = 0;
    while (tier < TIER_EDGES.length && attendance < TIER_EDGES[tier]) {
      tier++;
    }
    return tier;
  }

  // a tier that can't form groups joins the neighbouring tier closest in attendance
  private void mergeSmallTiers(List<Pool> tiers) {
    while (tiers.size() > 1) {
      int small = IntStream.range(0, tiers.size()).filter(i -> !splits(tiers.get(i).students.size()))
          .findFirst().orElse(-1);
      if (small < 0) {
        return;
      }
      tiers.get(closestNeighbour(tiers, small)).students.addAll(tiers.remove(small).students);
    }
  }

  private int closestNeighbour(List<Pool> tiers, int tier) {
    if (tier == 0) {
      return 1;
    }
    if (tier == tiers.size() - 1) {
      return tier - 1;
    }
    double attendance = attendance(tiers.get(tier));
    double above = Math.abs(attendance(tiers.get(tier - 1)) - attendance);
    double below = Math.abs(attendance(tiers.get(tier + 1)) - attendance);
    return above <= below ? tier - 1 : tier + 1;
  }

  private double attendance(Pool pool) {
    return pool.students.stream().mapToDouble(i -> candidates.get(i).attendance()).average().orElseThrow();
  }

  // a pool that can't be split yet first, then where one more leaves the most groups at the optimal size (GR7)
  private void place(List<Pool> pools, int student) {
    if (pools.isEmpty()) {
      pools.add(new Pool(new ArrayList<>(List.of(student))));
      return;
    }
    pools.stream().max(Comparator.<Pool, Boolean>comparing(pool -> !splits(pool.students.size()))
        .thenComparingInt(this::gain)
        .thenComparingInt(pool -> -pool.students.size()))
        .orElseThrow().students.add(student);
  }

  // how many more groups end up at the optimal size with one more student
  private int gain(Pool pool) {
    int size = pool.students.size();
    if (splits(size) && !splits(size + 1)) {
      return Integer.MIN_VALUE;
    }
    return optimalGroups(size + 1) - optimalGroups(size);
  }

  private boolean splits(int students) {
    return MatchingProblem.groupCount(students, minSize, optimalSize, maxSize) > 0;
  }

  private int optimalGroups(int students) {
    return MatchingProblem.optimalGroups(students, minSize, optimalSize, maxSize);
  }

  private record Pool(List<Integer> students) {
  }

}
