package com.studymatch.matching;

import static com.studymatch.matching.MatchingProblem.AGREEABLENESS;
import static com.studymatch.matching.MatchingProblem.LOW_AGREEABLENESS;
import static com.studymatch.matching.MatchingProblem.HIGH_EXTRAVERSION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.studymatch.matching.MatchingProblem.Member;

class EditionScoresTest {

  @Test
  @DisplayName("Traits become z-scores within the edition, so they average 0")
  void traitsAreZScores() {
    // Arrange - agreeableness 0.0, 0.1, ..., 0.9
    List<Candidate> candidates = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      candidates.add(tested(i / 10.0, 0.5, 2.0 + i));
    }

    // Act
    List<Member> members = EditionScores.members(candidates);

    // Assert
    double sum = members.stream().mapToDouble(member -> member.traits()[AGREEABLENESS]).sum();
    assertEquals(0, sum, 1e-9);
    assertTrue(members.get(0).traits()[AGREEABLENESS] < members.get(9).traits()[AGREEABLENESS]);
  }

  @Test
  @DisplayName("The bottom 20% by rank are flagged, the same ones every time even when scores tie")
  void flagsByRankWithTies() {
    // Arrange - ten students, all with the same agreeableness
    List<Candidate> candidates = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      candidates.add(tested(0.5, 0.5, 10.0));
    }

    // Act
    List<Member> members = EditionScores.members(candidates);

    // Assert - two of ten, the first two in the list
    assertTrue(members.get(0).has(LOW_AGREEABLENESS));
    assertTrue(members.get(1).has(LOW_AGREEABLENESS));
    assertEquals(2, members.stream().filter(member -> member.has(LOW_AGREEABLENESS)).count());
  }

  @Test
  @DisplayName("The top third by extraversion are flagged as extraverts")
  void topThirdAreExtraverts() {
    // Arrange - extraversion 0.0 to 0.8
    List<Candidate> candidates = new ArrayList<>();
    for (int i = 0; i < 9; i++) {
      candidates.add(tested(0.5, i / 10.0, 10.0));
    }

    // Act
    List<Member> members = EditionScores.members(candidates);

    // Assert - 33% of 9 rounds to 3: the last three
    for (int i = 0; i < 9; i++) {
      assertEquals(i >= 6, members.get(i).has(HIGH_EXTRAVERSION));
    }
  }

  @Test
  @DisplayName("Without a test a student sits at the average with no flags, and doesn't move the others")
  void untestedStudentIsNeutral() {
    // Arrange
    List<Candidate> withoutUntested = List.of(tested(0.2, 0.5, 10.0), tested(0.8, 0.5, 14.0));
    List<Candidate> withUntested = List.of(withoutUntested.get(0), withoutUntested.get(1),
        new Candidate(null, null, 80.0, 1, "A"));

    // Act
    List<Member> before = EditionScores.members(withoutUntested);
    List<Member> after = EditionScores.members(withUntested);

    // Assert
    Member untested = after.get(2);
    for (double trait : untested.traits()) {
      assertEquals(0, trait);
    }
    assertEquals(0, untested.flags());
    assertEquals(before.get(0).traits()[AGREEABLENESS], after.get(0).traits()[AGREEABLENESS], 1e-9);
  }

  @Test
  @DisplayName("Without grades the level is left out (NaN), the others are z-scores among the graded")
  void levelWithoutGrades() {
    // Arrange
    List<Candidate> candidates = List.of(tested(0.5, 0.5, 10.0), tested(0.5, 0.5, 14.0),
        new Candidate(traits(0.5, 0.5), null, 90.0, 1, "A"));

    // Act
    List<Member> members = EditionScores.members(candidates);

    // Assert
    assertEquals(-1, members.get(0).level(), 1e-9);
    assertEquals(1, members.get(1).level(), 1e-9);
    assertFalse(members.get(2).hasLevel());
  }

  private static Candidate tested(double agreeableness, double extraversion, double level) {
    return new Candidate(traits(agreeableness, extraversion), level, 90.0, 1, "A");
  }

  private static Candidate.Traits traits(double agreeableness, double extraversion) {
    return new Candidate.Traits(0.5, 0.5, extraversion, agreeableness, 0.5);
  }

}
