package com.studymatch.matching;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MatchingBudgetTest {

  @Test
  @DisplayName("A count budget allows exactly that many calculations")
  void countBudgetStopsAtTheLimit() {
    // Arrange
    MatchingBudget budget = MatchingBudget.evaluations(3);

    // Act
    for (int i = 0; i < 3; i++) {
      assertTrue(budget.spend());
    }

    // Assert
    assertFalse(budget.spend());
    assertTrue(budget.isExhausted());
    assertEquals(3, budget.getUsed());
  }

  @Test
  @DisplayName("Halfway through a count budget the progress is 0.5")
  void progressHalfway() {
    // Arrange
    MatchingBudget budget = MatchingBudget.evaluations(4);

    // Act
    budget.spend();
    budget.spend();

    // Assert
    assertEquals(0.5, budget.progress(), 1e-9);
  }

  @Test
  @DisplayName("A time budget of zero is used up straight away")
  void zeroTimeBudget() {
    // Arrange
    MatchingBudget budget = MatchingBudget.time(Duration.ZERO);

    // Act + Assert
    assertFalse(budget.spend());
    assertEquals(1, budget.progress(), 1e-9);
  }

  @Test
  @DisplayName("A processor time budget of zero is used up straight away")
  void zeroCpuTimeBudget() {
    // Arrange
    MatchingBudget budget = MatchingBudget.cpuTime(Duration.ZERO);

    // Act + Assert
    assertFalse(budget.spend());
  }

  @Test
  @DisplayName("A count budget splits into equal shares of what's left")
  void countBudgetSplits() {
    // Arrange - 10 left after 2 are used
    MatchingBudget budget = MatchingBudget.evaluations(12);
    budget.spend();
    budget.spend();

    // Act
    MatchingBudget share = budget.split(5).getFirst();

    // Assert - 2 each
    assertTrue(share.spend());
    assertTrue(share.spend());
    assertFalse(share.spend());
  }

  @Test
  @DisplayName("A processor time budget can't be split for parallel runs")
  void cpuTimeBudgetDoesNotSplit() {
    // Arrange
    MatchingBudget budget = MatchingBudget.cpuTime(Duration.ofSeconds(1));

    // Act + Assert
    assertThrows(IllegalStateException.class, () -> budget.split(2));
  }

  @Test
  @DisplayName("A processor time budget lets work start while there's time left")
  void cpuTimeBudgetAllowsWork() {
    // Arrange
    MatchingBudget budget = MatchingBudget.cpuTime(Duration.ofSeconds(10));

    // Act + Assert
    assertTrue(budget.spend());
    assertEquals(1, budget.getUsed());
  }

}
