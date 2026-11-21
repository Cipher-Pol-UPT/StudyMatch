package com.studymatch.matching;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SeparationBitsTest {

  @ParameterizedTest(name = "{0} students")
  @CsvSource({ "20", "63", "64", "65", "130" })
  @DisplayName("The bitset check agrees with the loop over the members, around the 64 student edges too")
  void bitsAgreeWithLoop(int students) {
    // Arrange - 15% of the pairs separated, so plenty of both answers
    MatchingProblem problem = MatchingTestData.randomProblem(students, 0.15, students, 3, 4, 5);
    Grouping grouping = randomGrouping(problem, new Random(students));
    TrackedCost tracked = new TrackedCost(problem, grouping);

    for (int student = 0; student < problem.size(); student++) {
      for (int group = 0; group < grouping.groupCount(); group++) {
        int leaving = grouping.members(group).getFirst();

        // Act + Assert - nobody leaving, and the group's first member swapping out
        assertEquals(MatchingCost.canJoin(problem, grouping, student, group, -1),
            tracked.canJoin(student, group, -1));
        assertEquals(MatchingCost.canJoin(problem, grouping, student, group, leaving),
            tracked.canJoin(student, group, leaving));
      }
    }
  }

  @ParameterizedTest(name = "{0} students")
  @CsvSource({ "20", "64", "65" })
  @DisplayName("A group being built sees the same separations as the loop")
  void draftAgreesWithLoop(int students) {
    // Arrange
    MatchingProblem problem = MatchingTestData.randomProblem(students, 0.15, students, 3, 4, 5);
    Grouping grouping = randomGrouping(problem, new Random(students));

    for (int group = 0; group < grouping.groupCount(); group++) {
      GroupDraft draft = new GroupDraft(problem);
      grouping.members(group).forEach(draft::add);
      for (int student = 0; student < problem.size(); student++) {
        // Act + Assert
        assertEquals(!MatchingCost.canJoin(problem, grouping, student, group, -1), draft.conflictsWith(student));
      }
    }
  }

  // filled in a random order, so the separations land anywhere
  private static Grouping randomGrouping(MatchingProblem problem, Random random) {
    List<Integer> students = new ArrayList<>(IntStream.range(0, problem.size()).boxed().toList());
    Collections.shuffle(students, random);
    int[] order = students.stream().mapToInt(Integer::intValue).toArray();
    return new Grouping(problem.fill(order), problem.getGroupCount());
  }

}
