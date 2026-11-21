package com.studymatch.matching;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.studymatch.matching.MatchingProblem.Member;

class MatchingProblemTest {

  @ParameterizedTest(name = "{0} students at {1}/{2}/{3} make {4} groups")
  @CsvSource({
      "12, 3, 4, 5, 3", // all at the optimal size
      "10, 3, 4, 5, 3", // 4+3+3 beats 5+5, one group at the optimal size
      "9, 3, 4, 5, 2", // 5+4
      "8, 4, 5, 6, 2", // exactly groups * min
      "15, 3, 3, 3, 5", // fixed size
      "4, 4, 4, 4, 1" // one group, exactly the size
  })
  @DisplayName("The group count keeps the most groups at the optimal size")
  void groupCountPrefersOptimalSize(int students, int min, int optimal, int max, int expected) {
    // Act
    int groups = MatchingProblem.groupCount(students, min, optimal, max);

    // Assert
    assertEquals(expected, groups);
  }

  @ParameterizedTest(name = "{0} students at {1}/{2}/{3} can't be split")
  @CsvSource({
      "3, 4, 4, 5", // fewer than the minimum
      "7, 4, 4, 4", // between one and two full groups
      "0, 2, 3, 4" // nobody
  })
  @DisplayName("Without a valid split the group count is -1")
  void noValidSplit(int students, int min, int optimal, int max) {
    // Act
    int groups = MatchingProblem.groupCount(students, min, optimal, max);

    // Assert
    assertEquals(-1, groups);
  }

  @ParameterizedTest(name = "{0} students at {1}/{2}/{3} split as {4}")
  @CsvSource({
      "12, 3, 4, 5, '4 4 4'",
      "10, 3, 4, 5, '4 3 3'", // the leftover in as few groups as possible
      "9, 3, 4, 5, '5 4'",
      "11, 2, 4, 6, '4 4 3'"
  })
  @DisplayName("The planned sizes add up to the pool, most of them at the optimal size")
  void groupSizes(int students, int min, int optimal, int max, String expected) {
    // Arrange
    MatchingProblem problem = MatchingTestData.randomProblem(students, 0, 1, min, optimal, max);

    // Act
    int[] sizes = problem.groupSizes();

    // Assert - largest first, the order of the groups doesn't matter
    String sorted = Arrays.stream(sizes).boxed().sorted(Collections.reverseOrder()).map(String::valueOf)
        .collect(Collectors.joining(" "));
    assertEquals(expected, sorted);
  }

  @Test
  @DisplayName("A separation works both ways and is counted for both students")
  void separationsBothWays() {
    // Arrange
    Member member = new Member(new double[MatchingProblem.TRAITS], Double.NaN, 1, 0);
    List<int[]> separations = List.of(new int[] { 0, 1 }, new int[] { 0, 2 });

    // Act
    MatchingProblem problem = new MatchingProblem(Collections.nCopies(4, member), separations, 2, 2, 2);

    // Assert
    assertTrue(problem.separated(1, 0));
    assertFalse(problem.separated(1, 2));
    assertEquals(2, problem.separationCount(0));
    assertEquals(0, problem.separationCount(3));
  }

  @Test
  @DisplayName("A pool that can't be split is refused")
  void poolWithoutValidSplitIsRefused() {
    // Arrange
    Member member = new Member(new double[MatchingProblem.TRAITS], Double.NaN, 1, 0);
    List<Member> members = Collections.nCopies(3, member);

    // Act + Assert
    assertThrows(NoValidSplitException.class, () -> new MatchingProblem(members, List.of(), 4, 4, 5));
  }

  @Test
  @DisplayName("Members are equal by the content of their traits, and differ when any field does")
  void memberEqualityByContent() {
    // Arrange - two separate arrays with the same traits
    Member member = new Member(new double[] { 1, 2, 3, 4, 5 }, 0.5, 1, 2);
    Member same = new Member(new double[] { 1, 2, 3, 4, 5 }, 0.5, 1, 2);

    // Act + Assert
    assertEquals(member, same);
    assertEquals(member.hashCode(), same.hashCode());
    assertTrue(member.toString().contains("[1.0, 2.0, 3.0, 4.0, 5.0]"));
    assertNotEquals(member, new Member(new double[] { 1, 2, 3, 4, 6 }, 0.5, 1, 2));
    assertNotEquals(member, new Member(new double[] { 1, 2, 3, 4, 5 }, 0.6, 1, 2));
    assertNotEquals(member, new Member(new double[] { 1, 2, 3, 4, 5 }, 0.5, 3, 2));
    assertNotEquals(member, new Member(new double[] { 1, 2, 3, 4, 5 }, 0.5, 1, 3));
    assertNotEquals(member, "not a member");
  }

}
