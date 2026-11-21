package com.studymatch.matching;

import java.util.List;
import java.util.Random;

// one change to a grouping that a search can try and take back: a swap, a move or a rotation
sealed interface Step permits Step.Swap, Step.Move, Step.Rotation {

  // within the sizes and nobody ends up with someone they're separated from
  boolean allowed(MatchingProblem problem, TrackedCost tracked);

  // makes the change and returns the new cost
  double apply(TrackedCost tracked);

  void undo(TrackedCost tracked);

  // half swaps with someone random, half moves to a random group
  static Step random(MatchingProblem problem, TrackedCost tracked, Random random) {
    int student = random.nextInt(problem.size());
    if (random.nextBoolean()) {
      return new Swap(student, random.nextInt(problem.size()));
    }
    return new Move(student, tracked.groupOf(student), random.nextInt(problem.getGroupCount()));
  }

  // learning from another grouping: a pair that's together there but not here, the second brought into the first's
  // group with a swap; null when the pair picked is already together. Group numbers mean nothing across groupings,
  // so this copies who's together instead
  static Step learnPair(MatchingProblem problem, TrackedCost source, TrackedCost teacher, Random random) {
    int student = random.nextInt(problem.size());
    List<Integer> together = teacher.members(teacher.groupOf(student));
    int mate = together.get(random.nextInt(together.size()));
    if (mate == student || source.groupOf(mate) == source.groupOf(student)) {
      return null;
    }
    List<Integer> group = source.members(source.groupOf(student));
    int leaving = group.get(random.nextInt(group.size()));
    return leaving == student ? null : new Swap(mate, leaving);
  }

  // a random step that's allowed, applied; NaN when none was found in a few tries
  static double applyRandom(MatchingProblem problem, TrackedCost tracked, Random random) {
    for (int attempt = 0; attempt < 20; attempt++) {
      Step step = random(problem, tracked, random);
      if (step.allowed(problem, tracked)) {
        return step.apply(tracked);
      }
    }
    return Double.NaN;
  }

  // two students in different groups trade places; sizes don't change
  record Swap(int first, int second) implements Step {

    @Override
    public boolean allowed(MatchingProblem problem, TrackedCost tracked) {
      int firstGroup = tracked.groupOf(first);
      int secondGroup = tracked.groupOf(second);
      return firstGroup != secondGroup && tracked.canJoin(first, secondGroup, second)
          && tracked.canJoin(second, firstGroup, first);
    }

    @Override
    public double apply(TrackedCost tracked) {
      return tracked.swap(first, second);
    }

    @Override
    public void undo(TrackedCost tracked) {
      tracked.swap(first, second);
    }

  }

  // one student goes to another group, if both groups stay within min-max
  record Move(int student, int from, int to) implements Step {

    @Override
    public boolean allowed(MatchingProblem problem, TrackedCost tracked) {
      return from != to && tracked.size(from) - 1 >= problem.getMinSize()
          && tracked.size(to) + 1 <= problem.getMaxSize() && tracked.canJoin(student, to, -1);
    }

    @Override
    public double apply(TrackedCost tracked) {
      return tracked.move(student, to);
    }

    @Override
    public void undo(TrackedCost tracked) {
      tracked.move(student, from);
    }

  }

  // three students in three groups: first into second's group, second into third's, third into first's
  record Rotation(int first, int second, int third) implements Step {

    @Override
    public boolean allowed(MatchingProblem problem, TrackedCost tracked) {
      int firstGroup = tracked.groupOf(first);
      int secondGroup = tracked.groupOf(second);
      int thirdGroup = tracked.groupOf(third);
      return firstGroup != secondGroup && thirdGroup != firstGroup && thirdGroup != secondGroup
          && tracked.canJoin(first, secondGroup, second) && tracked.canJoin(second, thirdGroup, third)
          && tracked.canJoin(third, firstGroup, first);
    }

    // two swaps in a row end in the rotation
    @Override
    public double apply(TrackedCost tracked) {
      tracked.swap(first, second);
      return tracked.swap(second, third);
    }

    @Override
    public void undo(TrackedCost tracked) {
      tracked.swap(second, third);
      tracked.swap(first, second);
    }

  }

}
