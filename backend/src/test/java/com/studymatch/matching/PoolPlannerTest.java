package com.studymatch.matching;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PoolPlannerTest {

  private static final Candidate.Traits TRAITS = new Candidate.Traits(0.5, 0.5, 0.5, 0.5, 0.5);

  @ParameterizedTest(name = "{0}% is tier {1}")
  @CsvSource({ "100, 0", "90, 0", "89.9, 1", "75, 1", "74.9, 2", "50, 2", "49.9, 3", "0, 3" })
  @DisplayName("Attendance falls in four tiers: 90% and up, 75 to 90, 50 to 75, below 50")
  void tierEdges(double attendance, int tier) {
    // Act + Assert
    assertEquals(tier, PoolPlanner.tierOf(attendance));
  }

  @Test
  @DisplayName("Two tiers that can each form groups are matched apart")
  void fullTiersStayApart() {
    // Arrange
    List<Candidate> candidates = new ArrayList<>();
    add(candidates, 8, 95.0, "A");
    add(candidates, 8, 60.0, "A");

    // Act
    List<List<Integer>> pools = plan(candidates, false);

    // Assert
    assertEquals(List.of(range(0, 8), range(8, 16)), sorted(pools));
  }

  @Test
  @DisplayName("A tier too small for a group joins the neighbouring tier closest in attendance")
  void smallTierJoinsClosestNeighbour() {
    // Arrange - the 80% pair is 15 points from the top tier and 25 from the 55% one
    List<Candidate> candidates = new ArrayList<>();
    add(candidates, 8, 95.0, "A");
    add(candidates, 2, 80.0, "A");
    add(candidates, 8, 55.0, "A");

    // Act
    List<List<Integer>> pools = plan(candidates, false);

    // Assert
    assertEquals(List.of(range(0, 10), range(10, 18)), sorted(pools));
  }

  @Test
  @DisplayName("A small lowest tier joins the tier above it")
  void smallLowestTierJoinsAbove() {
    // Arrange
    List<Candidate> candidates = new ArrayList<>();
    add(candidates, 8, 95.0, "A");
    add(candidates, 2, 30.0, "A");

    // Act
    List<List<Integer>> pools = plan(candidates, false);

    // Assert
    assertEquals(List.of(range(0, 10)), sorted(pools));
  }

  @Test
  @DisplayName("A small middle tier joins the tier below when that one is closer in attendance")
  void smallMiddleTierJoinsBelow() {
    // Arrange - the 60% pair is 35 points from the top tier and 15 from the 45% one
    List<Candidate> candidates = new ArrayList<>();
    add(candidates, 8, 95.0, "A");
    add(candidates, 2, 60.0, "A");
    add(candidates, 8, 45.0, "A");

    // Act
    List<List<Integer>> pools = plan(candidates, false);

    // Assert
    assertEquals(List.of(range(0, 8), range(8, 18)), sorted(pools));
  }

  @Test
  @DisplayName("A student without a tier never goes to a pool they would make impossible to split")
  void fillerNeverBreaksAPool() {
    // Arrange - groups of 4 or 5: a pool of 5 can't take a sixth, a pool of 8 can take a ninth (4+5)
    List<Candidate> candidates = new ArrayList<>();
    add(candidates, 8, 95.0, "A");
    add(candidates, 5, 60.0, "A");
    add(candidates, 1, null, "A");

    // Act
    List<List<Integer>> pools = PoolPlanner.plan(candidates, 4, 4, 5, false);

    // Assert
    List<Integer> upper = new ArrayList<>(range(0, 8));
    upper.add(13);
    assertEquals(List.of(upper, range(8, 13)), sorted(pools));
  }

  @Test
  @DisplayName("A student without a tier joins the pool where they add a group at the optimal size")
  void fillerGoesWhereItHelps() {
    // Arrange - 8 is 4+4 already, 7 is 4+3 and becomes 4+4 with one more
    List<Candidate> candidates = new ArrayList<>();
    add(candidates, 8, 95.0, "A");
    add(candidates, 7, 60.0, "A");
    add(candidates, 1, null, "A");

    // Act
    List<List<Integer>> pools = plan(candidates, false);

    // Assert
    List<Integer> lower = new ArrayList<>(range(8, 15));
    lower.add(15);
    assertEquals(List.of(range(0, 8), lower), sorted(pools));
  }

  @Test
  @DisplayName("On a cold start nobody has attendance, so the whole class is one pool")
  void coldStartIsOnePool() {
    // Arrange
    List<Candidate> candidates = new ArrayList<>();
    add(candidates, 10, null, "A");

    // Act
    List<List<Integer>> pools = plan(candidates, false);

    // Assert
    assertEquals(List.of(range(0, 10)), sorted(pools));
  }

  @Test
  @DisplayName("Students without a test are grouped among themselves when there are enough of them")
  void untestedFormTheirOwnPool() {
    // Arrange
    List<Candidate> candidates = new ArrayList<>();
    add(candidates, 8, 95.0, "A");
    addUntested(candidates, 4, "A");

    // Act
    List<List<Integer>> pools = plan(candidates, false);

    // Assert
    assertEquals(List.of(range(0, 8), range(8, 12)), sorted(pools));
  }

  @Test
  @DisplayName("Too few students without a test to form a group fill the other pools")
  void fewUntestedFillTheOthers() {
    // Arrange
    List<Candidate> candidates = new ArrayList<>();
    add(candidates, 8, 95.0, "A");
    addUntested(candidates, 2, "A");

    // Act
    List<List<Integer>> pools = plan(candidates, false);

    // Assert
    assertEquals(List.of(range(0, 10)), sorted(pools));
  }

  @Test
  @DisplayName("When a pool still can't be split the whole class becomes one pool")
  void leftoverPoolFallsBackToWholeClass() {
    // Arrange - two tested students can't form a group, the three untested can
    List<Candidate> candidates = new ArrayList<>();
    add(candidates, 2, 95.0, "A");
    addUntested(candidates, 3, "A");

    // Act
    List<List<Integer>> pools = plan(candidates, false);

    // Assert
    assertEquals(List.of(range(0, 5)), sorted(pools));
  }

  @Test
  @DisplayName("Groups from the same class keep every pool inside one class")
  void sameClassNeverMixes() {
    // Arrange - the same attendance, so only the class keeps them apart
    List<Candidate> candidates = new ArrayList<>();
    add(candidates, 6, 95.0, "B");
    add(candidates, 6, 95.0, "A");

    // Act
    List<List<Integer>> pools = plan(candidates, true);

    // Assert
    assertEquals(List.of(range(0, 6), range(6, 12)), sorted(pools));
  }

  @Test
  @DisplayName("A class that can't be split within the sizes is refused")
  void classWithoutValidSplit() {
    // Arrange
    List<Candidate> candidates = new ArrayList<>();
    add(candidates, 6, 95.0, "A");
    add(candidates, 2, 95.0, "B");

    // Act + Assert
    assertThrows(NoValidSplitException.class, () -> plan(candidates, true));
  }

  // groups of 3 to 5, 4 at best
  private static List<List<Integer>> plan(List<Candidate> candidates, boolean sameClass) {
    List<List<Integer>> pools = PoolPlanner.plan(candidates, 3, 4, 5, sameClass);
    List<Integer> everyone = pools.stream().flatMap(List::stream).sorted().toList();
    assertEquals(range(0, candidates.size()), everyone);
    for (List<Integer> pool : pools) {
      assertTrue(MatchingProblem.groupCount(pool.size(), 3, 4, 5) > 0);
    }
    return pools;
  }

  private static void add(List<Candidate> candidates, int count, Double attendance, String classSection) {
    for (int i = 0; i < count; i++) {
      candidates.add(new Candidate(TRAITS, null, attendance, 1, classSection));
    }
  }

  private static void addUntested(List<Candidate> candidates, int count, String classSection) {
    for (int i = 0; i < count; i++) {
      candidates.add(new Candidate(null, null, 90.0, 1, classSection));
    }
  }

  // each pool sorted, the pools by their first student, so the order they were made in doesn't matter
  private static List<List<Integer>> sorted(List<List<Integer>> pools) {
    List<List<Integer>> sorted = new ArrayList<>(pools.stream().map(pool -> {
      List<Integer> copy = new ArrayList<>(pool);
      Collections.sort(copy);
      return (List<Integer>) copy;
    }).toList());
    sorted.sort((first, second) -> Integer.compare(first.getFirst(), second.getFirst()));
    return sorted;
  }

  private static List<Integer> range(int from, int to) {
    return IntStream.range(from, to).boxed().toList();
  }

}
