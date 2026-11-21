package com.studymatch.matching;

import static com.studymatch.matching.MatchingProblem.EMOTIONAL_STABILITY;
import static com.studymatch.matching.MatchingProblem.HIGH_CONSCIENTIOUSNESS;
import static com.studymatch.matching.MatchingProblem.HIGH_EXTRAVERSION;
import static com.studymatch.matching.MatchingProblem.LOW_AGREEABLENESS;
import static com.studymatch.matching.MatchingProblem.LOW_CONSCIENTIOUSNESS;
import static com.studymatch.matching.MatchingProblem.LOW_EMOTIONAL_STABILITY;
import static com.studymatch.matching.MatchingProblem.OPENNESS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.studymatch.matching.MatchingProblem.Member;

class MatchingCostTest {

  private static final double DELTA = 1e-9;
  private static final int PORTUGUESE = 1;
  private static final int ENGLISH = 1 << 1;

  private static Member member(int flags) {
    return new Member(new double[MatchingProblem.TRAITS], Double.NaN, PORTUGUESE, flags);
  }

  private static Member member(int flags, int trait, double value, double level) {
    double[] traits = new double[MatchingProblem.TRAITS];
    traits[trait] = value;
    return new Member(traits, level, PORTUGUESE, flags);
  }

  private static Member withLevel(int flags, double level) {
    return new Member(new double[MatchingProblem.TRAITS], level, PORTUGUESE, flags);
  }

  // one extravert and three average students, all speaking the same language: nothing to penalise
  private static List<Member> neutralGroup() {
    return new ArrayList<>(List.of(member(HIGH_EXTRAVERSION), member(0), member(0), member(0)));
  }

  // everyone in one group at the optimal size, so the fairness terms are 0
  private static double costAsOneGroup(List<Member> members) {
    MatchingProblem problem = new MatchingProblem(members, List.of(), members.size(), members.size(),
        members.size());
    return MatchingCost.cost(problem, new Grouping(new int[members.size()], 1));
  }

  // personality rules, per group

  @Test
  @DisplayName("A neutral group on a cold start costs nothing")
  void neutralGroupCostsNothing() {
    // Act
    double cost = costAsOneGroup(neutralGroup());

    // Assert - no levels either, so the grade terms add nothing (GR6)
    assertEquals(0, cost, DELTA);
  }

  @ParameterizedTest(name = "{0} low agreeableness cost {1}")
  @CsvSource({ "1, 0", "2, 3", "3, 12" })
  @DisplayName("One low agreeableness student is fine, more grow quadratically")
  void lowAgreeableness(int lowStudents, double expected) {
    // Arrange
    List<Member> members = neutralGroup();
    for (int i = 0; i < lowStudents; i++) {
      members.set(i + 1, member(LOW_AGREEABLENESS));
    }

    // Act
    double cost = costAsOneGroup(members);

    // Assert
    assertEquals(expected, cost, DELTA);
  }

  @Test
  @DisplayName("Two low emotional stability students cost 3")
  void twoLowEmotionalStability() {
    // Arrange
    List<Member> members = neutralGroup();
    members.set(1, member(LOW_EMOTIONAL_STABILITY));
    members.set(2, member(LOW_EMOTIONAL_STABILITY));

    // Act
    double cost = costAsOneGroup(members);

    // Assert
    assertEquals(3, cost, DELTA);
  }

  @ParameterizedTest(name = "a gap of {0} costs {1}")
  @CsvSource({ "2.0, 0", "3.0, 3" })
  @DisplayName("An emotional stability gap above 2 is penalised by how much it goes over")
  void emotionalStabilityGap(double gap, double expected) {
    // Arrange
    List<Member> members = neutralGroup();
    members.set(1, member(0, EMOTIONAL_STABILITY, gap / 2, Double.NaN));
    members.set(2, member(0, EMOTIONAL_STABILITY, -gap / 2, Double.NaN));

    // Act
    double cost = costAsOneGroup(members);

    // Assert
    assertEquals(expected, cost, DELTA);
  }

  @Test
  @DisplayName("A hard worker with a low conscientiousness student costs 2")
  void highAndLowConscientiousness() {
    // Arrange
    List<Member> members = neutralGroup();
    members.set(1, member(HIGH_CONSCIENTIOUSNESS));
    members.set(2, member(LOW_CONSCIENTIOUSNESS));

    // Act
    double cost = costAsOneGroup(members);

    // Assert
    assertEquals(2, cost, DELTA);
  }

  @ParameterizedTest(name = "{0} extraverts cost {1}")
  @CsvSource({ "0, 1.5", "1, 0", "2, 0", "3, 1.5" })
  @DisplayName("One or two extraverts per group, none or three or more are penalised")
  void extraverts(int extraverts, double expected) {
    // Arrange
    List<Member> members = new ArrayList<>();
    for (int i = 0; i < 4; i++) {
      members.add(member(i < extraverts ? HIGH_EXTRAVERSION : 0));
    }

    // Act
    double cost = costAsOneGroup(members);

    // Assert
    assertEquals(expected, cost, DELTA);
  }

  // language and size

  @Test
  @DisplayName("A group without a shared language costs 10 (GR4)")
  void noSharedLanguage() {
    // Arrange
    List<Member> members = neutralGroup();
    members.set(1, new Member(new double[MatchingProblem.TRAITS], Double.NaN, ENGLISH, 0));

    // Act
    double cost = costAsOneGroup(members);

    // Assert
    assertEquals(10, cost, DELTA);
  }

  @Test
  @DisplayName("A group that isn't at the optimal size costs 0.5")
  void groupOffOptimalSize() {
    // Arrange - 4 students, optimal 3
    MatchingProblem problem = new MatchingProblem(neutralGroup(), List.of(), 3, 3, 4);

    // Act
    double cost = MatchingCost.cost(problem, new Grouping(new int[4], 1));

    // Assert
    assertEquals(0.5, cost, DELTA);
  }

  // grades

  @Test
  @DisplayName("A group where everyone has the same level costs 1.25")
  void sameLevelGroup() {
    // Arrange - spread 0, so 2.5 * (0.5 - 0)
    List<Member> members = List.of(withLevel(HIGH_EXTRAVERSION, 0), withLevel(0, 0), withLevel(0, 0),
        withLevel(0, 0));

    // Act
    double cost = costAsOneGroup(members);

    // Assert
    assertEquals(1.25, cost, DELTA);
  }

  @Test
  @DisplayName("A group with mixed levels costs nothing")
  void mixedLevelGroup() {
    // Arrange - spread 1
    List<Member> members = List.of(withLevel(HIGH_EXTRAVERSION, -1), withLevel(0, -1), withLevel(0, 1),
        withLevel(0, 1));

    // Act
    double cost = costAsOneGroup(members);

    // Assert
    assertEquals(0, cost, DELTA);
  }

  // fairness across groups

  @Test
  @DisplayName("Two groups with different openness averages pay the openness fairness term")
  void opennessFairness() {
    // Arrange - means 1 and -1, variance 1, weight 0.5
    List<Member> members = List.of(member(HIGH_EXTRAVERSION, OPENNESS, 1, Double.NaN),
        member(0, OPENNESS, 1, Double.NaN), member(HIGH_EXTRAVERSION, OPENNESS, -1, Double.NaN),
        member(0, OPENNESS, -1, Double.NaN));
    MatchingProblem problem = new MatchingProblem(members, List.of(), 2, 2, 2);

    // Act
    double cost = MatchingCost.cost(problem, new Grouping(new int[] { 0, 0, 1, 1 }, 2));

    // Assert
    assertEquals(0.5, cost, DELTA);
  }

  @Test
  @DisplayName("Two groups with different level averages pay the level fairness term")
  void levelFairness() {
    // Arrange - means 1 and -1, variance 1, weight 2.5; each spread is 0.5, so no spread penalty
    List<Member> members = List.of(withLevel(HIGH_EXTRAVERSION, 0.5), withLevel(0, 1.5),
        withLevel(HIGH_EXTRAVERSION, -0.5), withLevel(0, -1.5));
    MatchingProblem problem = new MatchingProblem(members, List.of(), 2, 2, 2);

    // Act
    double cost = MatchingCost.cost(problem, new Grouping(new int[] { 0, 0, 1, 1 }, 2));

    // Assert
    assertEquals(2.5, cost, DELTA);
  }

  // hard rules

  @Test
  @DisplayName("Groups within the sizes and with no separated pair are valid")
  void validGrouping() {
    // Arrange
    MatchingProblem problem = new MatchingProblem(neutralGroup(), List.of(new int[] { 0, 2 }), 2, 2, 2);

    // Act + Assert
    assertTrue(MatchingCost.isValid(problem, new Grouping(new int[] { 0, 0, 1, 1 }, 2)));
  }

  @Test
  @DisplayName("A separated pair in the same group is invalid")
  void separatedPairTogether() {
    // Arrange
    MatchingProblem problem = new MatchingProblem(neutralGroup(), List.of(new int[] { 0, 1 }), 2, 2, 2);

    // Act + Assert
    assertFalse(MatchingCost.isValid(problem, new Grouping(new int[] { 0, 0, 1, 1 }, 2)));
  }

  @Test
  @DisplayName("A group below the minimum size is invalid")
  void groupTooSmall() {
    // Arrange
    MatchingProblem problem = new MatchingProblem(neutralGroup(), List.of(), 2, 2, 2);

    // Act + Assert - 3 and 1
    assertFalse(MatchingCost.isValid(problem, new Grouping(new int[] { 0, 0, 0, 1 }, 2)));
  }

}
