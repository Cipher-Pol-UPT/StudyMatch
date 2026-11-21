package com.studymatch.matching;

// one enrolled student as raw numbers, before anything is ranked against the edition
// traits: the test's scores (0 to 1), null without a test; level: the best grade evidence there is (GR6), null
// without grades; attendance: the best attendance evidence there is, null with a statute or before any lesson (GR7)
public record Candidate(Traits traits, Double level, Double attendance, int languages, String classSection) {

  public record Traits(double openness, double conscientiousness, double extraversion, double agreeableness,
      double emotionalStability) {

    double get(int trait) {
      return switch (trait) {
        case MatchingProblem.OPENNESS -> openness;
        case MatchingProblem.CONSCIENTIOUSNESS -> conscientiousness;
        case MatchingProblem.EXTRAVERSION -> extraversion;
        case MatchingProblem.AGREEABLENESS -> agreeableness;
        default -> emotionalStability;
      };
    }

  }

  public boolean hasTest() {
    return traits != null;
  }

  // no attendance tier, so they fill the pools that need members (GR7)
  public boolean isFiller() {
    return attendance == null;
  }

}
