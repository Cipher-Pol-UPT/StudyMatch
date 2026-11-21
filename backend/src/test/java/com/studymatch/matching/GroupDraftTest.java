package com.studymatch.matching;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.studymatch.matching.MatchingProblem.Member;

class GroupDraftTest {

  private static final double DELTA = 1e-9;

  @ParameterizedTest(name = "seed {0}")
  @CsvSource({ "1", "2", "3", "4", "5" })
  @DisplayName("While a group grows, its cost always equals the group cost worked out from scratch")
  void matchesGroupCost(long seed) {
    // Arrange - a class of 40, groups grown up to 8 so every rule gets a chance to kick in
    MatchingProblem problem = MatchingTestData.randomProblem(40, 0, seed, 3, 4, 5);
    List<Integer> students = new ArrayList<>();
    for (int student = 0; student < problem.size(); student++) {
      students.add(student);
    }
    Collections.shuffle(students, new Random(seed));

    for (int start = 0; start < students.size(); start += 8) {
      GroupDraft draft = new GroupDraft(problem);
      for (int student : students.subList(start, Math.min(start + 8, students.size()))) {
        // Act
        double predicted = draft.costWith(student);
        draft.add(student);

        // Assert
        double expected = MatchingCost.groupCost(problem, members(problem, draft.students()));
        assertEquals(expected, predicted, DELTA);
        assertEquals(expected, draft.cost(), DELTA);
      }
    }
  }

  @ParameterizedTest(name = "seed {0}")
  @CsvSource({ "1", "2", "3" })
  @DisplayName("The fairness change of adding a student matches the group's averages worked out from scratch")
  void fairnessMatchesAverages(long seed) {
    // Arrange
    MatchingProblem problem = MatchingTestData.randomProblem(40, 0, seed, 3, 4, 5);
    GroupDraft draft = new GroupDraft(problem);
    List<Integer> students = new ArrayList<>();

    for (int student = 0; student < 6; student++) {
      List<Integer> joined = new ArrayList<>(students);
      joined.add(student);

      // Act
      double change = draft.fairnessWith(student);

      // Assert
      double expected = (share(problem, joined) - share(problem, students)) / problem.getGroupCount();
      assertEquals(expected, change, DELTA);
      draft.add(student);
      students.add(student);
    }
  }

  // the group's part of the fairness terms: weighted (its average - the pool's)^2, 0 for nobody
  private static double share(MatchingProblem problem, List<Integer> students) {
    if (students.isEmpty()) {
      return 0;
    }
    double share = 0;
    for (int trait = 0; trait < MatchingProblem.TRAITS; trait++) {
      int current = trait;
      double average = students.stream().mapToDouble(s -> problem.member(s).traits()[current]).average()
          .orElseThrow();
      share += MatchingCost.FAIRNESS_WEIGHTS[trait] * Math.pow(average - problem.traitAverage(trait), 2);
    }
    double[] levels = students.stream().map(problem::member).filter(Member::hasLevel).mapToDouble(Member::level)
        .toArray();
    if (levels.length > 0) {
      share += MatchingCost.LEVEL_WEIGHT * Math.pow(MatchingCost.mean(levels) - problem.levelAverage(), 2);
    }
    return share;
  }

  @ParameterizedTest(name = "seed {0}")
  @CsvSource({ "1", "2" })
  @DisplayName("An empty group costs the same as the group cost of nobody")
  void emptyGroup(long seed) {
    // Arrange
    MatchingProblem problem = MatchingTestData.randomProblem(20, 0, seed, 3, 4, 5);

    // Act + Assert
    assertEquals(MatchingCost.groupCost(problem, List.of()), new GroupDraft(problem).cost(), DELTA);
  }

  private static List<Member> members(MatchingProblem problem, List<Integer> students) {
    return students.stream().map(problem::member).toList();
  }

}
