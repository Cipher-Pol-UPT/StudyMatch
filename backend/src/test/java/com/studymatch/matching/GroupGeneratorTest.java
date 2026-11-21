package com.studymatch.matching;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GroupGeneratorTest {

  private final GroupGenerator generator = new GroupGenerator(new BeeColonyMatching(), Duration.ofMillis(100));

  @Test
  @DisplayName("A mixed class: everyone placed once, every group within the sizes and no separated pair together")
  void mixedClassGivesValidGroups() {
    // Arrange - four tiers, fillers, students without a test, 5% of the pairs separated
    Random random = new Random(1);
    List<Candidate> candidates = mixedClass(random, "A");
    List<int[]> separations = randomSeparations(candidates.size(), random);

    // Act
    List<List<Integer>> groups = generator.generate(candidates, separations, 3, 4, 5, false, new Random(2));

    // Assert
    assertEquals(IntStream.range(0, candidates.size()).boxed().toList(),
        groups.stream().flatMap(List::stream).sorted().toList());
    for (List<Integer> group : groups) {
      assertTrue(group.size() >= 3 && group.size() <= 5);
      for (int[] pair : separations) {
        assertFalse(group.contains(pair[0]) && group.contains(pair[1]));
      }
    }
  }

  @Test
  @DisplayName("Groups from the same class have every member in one class")
  void sameClassGroups() {
    // Arrange
    Random random = new Random(3);
    List<Candidate> candidates = new ArrayList<>(mixedClass(random, "A"));
    candidates.addAll(mixedClass(random, "B"));

    // Act
    List<List<Integer>> groups = generator.generate(candidates, List.of(), 3, 4, 5, true, new Random(4));

    // Assert
    for (List<Integer> group : groups) {
      String classSection = candidates.get(group.getFirst()).classSection();
      assertTrue(group.stream().allMatch(student -> candidates.get(student).classSection().equals(classSection)));
    }
  }

  @Test
  @DisplayName("Separations that leave no valid grouping are reported")
  void impossibleSeparations() {
    // Arrange - groups of 2 and the first student separated from everyone
    List<Candidate> candidates = new ArrayList<>();
    for (int i = 0; i < 4; i++) {
      candidates.add(new Candidate(null, null, null, 1, "A"));
    }
    List<int[]> separations = List.of(new int[] { 0, 1 }, new int[] { 0, 2 }, new int[] { 0, 3 });

    // Act + Assert
    assertThrows(UnmetSeparationsException.class,
        () -> generator.generate(candidates, separations, 2, 2, 2, false, new Random(1)));
  }

  // 41 students: tested ones in every tier, a few fillers and a few without a test
  private static List<Candidate> mixedClass(Random random, String classSection) {
    List<Candidate> candidates = new ArrayList<>();
    for (int i = 0; i < 41; i++) {
      Candidate.Traits traits = i % 10 == 0 ? null
          : new Candidate.Traits(random.nextDouble(), random.nextDouble(), random.nextDouble(), random.nextDouble(),
              random.nextDouble());
      Double attendance = i % 7 == 0 ? null : 30 + 70 * random.nextDouble();
      Double level = i % 5 == 0 ? null : 8 + 10 * random.nextDouble();
      candidates.add(new Candidate(traits, level, attendance, 1 | (random.nextBoolean() ? 2 : 0), classSection));
    }
    return candidates;
  }

  private static List<int[]> randomSeparations(int students, Random random) {
    List<int[]> separations = new ArrayList<>();
    for (int first = 0; first < students; first++) {
      for (int second = first + 1; second < students; second++) {
        if (random.nextDouble() < 0.05) {
          separations.add(new int[] { first, second });
        }
      }
    }
    return separations;
  }

}
