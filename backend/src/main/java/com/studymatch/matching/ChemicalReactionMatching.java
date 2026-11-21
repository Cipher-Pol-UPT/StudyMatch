package com.studymatch.matching;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

// Chemical Reaction Optimization (Lam & Li): groupings are molecules with potential energy (the cost) and kinetic
// energy (how much worse they may get); four reactions change them while the total energy is kept
public final class ChemicalReactionMatching implements MatchingAlgorithm {

  // molecules: starting population; initialKinetic: kinetic energy each starts with; keLossRate: least share of
  // kinetic energy kept after a wall hit; collisionRate: share of reactions between two molecules;
  // decomposeAfter: hits without a new best before a molecule splits; synthesizeBelow: kinetic energy under which
  // two molecules merge
  public record Settings(int molecules, double initialKinetic, double keLossRate, double collisionRate,
      int decomposeAfter, double synthesizeBelow) {
  }

  // from MatchingTuning: decomposing rarely mattered most
  public static final Settings DEFAULTS = new Settings(10, 2, 0.2, 0.2, 2_000, 0.05);

  private static final class Molecule {

    private TrackedCost structure;
    private double kinetic;
    private int hits;
    private int bestHit;
    private double bestCost;

    Molecule(TrackedCost structure, double kinetic) {
      this.structure = structure;
      this.kinetic = kinetic;
      this.bestCost = structure.total();
    }

    double potential() {
      return structure.total();
    }

    // takes the new structure and kinetic energy after a reaction
    void become(TrackedCost next, double nextKinetic) {
      structure = next;
      kinetic = nextKinetic;
      hits++;
      if (next.total() < bestCost) {
        bestCost = next.total();
        bestHit = hits;
      }
    }

  }

  private final GreedyMatching greedy = new GreedyMatching();
  private final Settings settings;
  private double buffer;

  public ChemicalReactionMatching() {
    this(DEFAULTS);
  }

  public ChemicalReactionMatching(Settings settings) {
    this.settings = settings;
  }

  @Override
  public Grouping solve(MatchingProblem problem, MatchingBudget budget, Random random) {
    buffer = 0;
    List<Molecule> molecules = new ArrayList<>();
    for (int molecule = 0; molecule < settings.molecules(); molecule++) {
      molecules.add(new Molecule(new TrackedCost(problem, greedy.solve(problem, budget, random)),
          settings.initialKinetic()));
    }
    if (problem.getGroupCount() < 2) {
      return molecules.getFirst().structure.grouping();
    }
    Grouping best = molecules.getFirst().structure.grouping();
    double bestCost = molecules.getFirst().potential();

    while (!budget.isExhausted()) {
      react(problem, molecules, budget, random);
      for (Molecule molecule : molecules) {
        if (molecule.potential() < bestCost) {
          best = molecule.structure.grouping();
          bestCost = molecule.potential();
        }
      }
    }
    return best;
  }

  private void react(MatchingProblem problem, List<Molecule> molecules, MatchingBudget budget, Random random) {
    if (molecules.size() < 2 || random.nextDouble() > settings.collisionRate()) {
      Molecule molecule = molecules.get(random.nextInt(molecules.size()));
      if (molecule.hits - molecule.bestHit > settings.decomposeAfter()) {
        decompose(problem, molecules, molecule, budget, random);
      } else {
        hitWall(problem, molecule, budget, random);
      }
      return;
    }
    int first = random.nextInt(molecules.size());
    int second = (first + 1 + random.nextInt(molecules.size() - 1)) % molecules.size();
    Molecule one = molecules.get(first);
    Molecule two = molecules.get(second);
    if (one.kinetic <= settings.synthesizeBelow() && two.kinetic <= settings.synthesizeBelow()) {
      synthesize(problem, molecules, one, two, budget, random);
    } else {
      collide(problem, one, two, budget, random);
    }
  }

  // one small step; kept if the molecule's energy covers it, part of the kinetic energy goes to the buffer
  private void hitWall(MatchingProblem problem, Molecule molecule, MatchingBudget budget, Random random) {
    TrackedCost next = changed(problem, molecule.structure, 1, budget, random);
    double energy = molecule.potential() + molecule.kinetic;
    if (energy >= next.total()) {
      double kept = settings.keLossRate() + random.nextDouble() * (1 - settings.keLossRate());
      buffer += (energy - next.total()) * (1 - kept);
      molecule.become(next, (energy - next.total()) * kept);
    } else {
      molecule.hits++;
    }
  }

  // a molecule stuck for long splits into two very different ones, paid with its energy and the buffer
  private void decompose(MatchingProblem problem, List<Molecule> molecules, Molecule molecule,
      MatchingBudget budget, Random random) {
    int jump = Math.max(2, problem.size() / 4);
    TrackedCost first = changed(problem, molecule.structure, jump, budget, random);
    TrackedCost second = changed(problem, molecule.structure, jump, budget, random);
    double surplus = molecule.potential() + molecule.kinetic - first.total() - second.total();
    if (surplus < 0) {
      double fromBuffer = random.nextDouble() * random.nextDouble() * buffer;
      if (surplus + fromBuffer < 0) {
        molecule.hits++;
        return;
      }
      buffer -= fromBuffer;
      surplus += fromBuffer;
    }
    double split = random.nextDouble();
    molecule.become(first, surplus * split);
    molecule.bestHit = molecule.hits;
    molecules.add(new Molecule(second, surplus * (1 - split)));
  }

  // two molecules hit each other: both take a step, kept if their joint energy covers it
  private void collide(MatchingProblem problem, Molecule one, Molecule two, MatchingBudget budget, Random random) {
    TrackedCost nextOne = changed(problem, one.structure, 1, budget, random);
    TrackedCost nextTwo = changed(problem, two.structure, 1, budget, random);
    double surplus = one.potential() + one.kinetic + two.potential() + two.kinetic - nextOne.total()
        - nextTwo.total();
    if (surplus < 0) {
      one.hits++;
      two.hits++;
      return;
    }
    double split = random.nextDouble();
    one.become(nextOne, surplus * split);
    two.become(nextTwo, surplus * (1 - split));
  }

  // two slow molecules merge: group numbers don't match between groupings, so the better one, with a step
  private void synthesize(MatchingProblem problem, List<Molecule> molecules, Molecule one, Molecule two,
      MatchingBudget budget, Random random) {
    Molecule better = one.potential() <= two.potential() ? one : two;
    TrackedCost merged = changed(problem, better.structure, 1, budget, random);
    double surplus = one.potential() + one.kinetic + two.potential() + two.kinetic - merged.total();
    if (surplus < 0) {
      one.hits++;
      two.hits++;
      return;
    }
    better.become(merged, surplus);
    molecules.remove(better == one ? two : one);
  }

  // a copy with this many random steps, each one a cost calculation
  private static TrackedCost changed(MatchingProblem problem, TrackedCost structure, int steps,
      MatchingBudget budget, Random random) {
    TrackedCost copy = new TrackedCost(problem, structure.grouping());
    for (int step = 0; step < steps && budget.spend(); step++) {
      Step.applyRandom(problem, copy, random);
    }
    return copy;
  }

}
