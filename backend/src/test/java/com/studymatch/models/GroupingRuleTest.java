package com.studymatch.models;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.studymatch.exceptions.ErrorCode;
import com.studymatch.exceptions.StudyMatchException;

class GroupingRuleTest {

  private GroupingRule rule;
  private Student ana;
  private Student bruno;

  @BeforeEach
  void setUp() {
    rule = new GroupingRule(2, 3, 4, false);
    ana = ModelTestData.student("Ana Test");
    bruno = ModelTestData.student("Bruno Test");
  }

  // sizes: 2 <= min <= optimal <= max

  @ParameterizedTest(name = "min={0} optimal={1} max={2} is valid")
  @CsvSource({ "2, 2, 2", "2, 3, 4", "3, 3, 5", "2, 4, 4" })
  @DisplayName("Sizes at or inside the limits are accepted")
  void validSizes(int min, int optimal, int max) {
    assertDoesNotThrow(() -> new GroupingRule(min, optimal, max, false));
  }

  @ParameterizedTest(name = "min={0} optimal={1} max={2} is invalid")
  @CsvSource({
      "1, 3, 4", // min below 2
      "4, 3, 5", // min above optimal
      "2, 5, 4"  // optimal above max
  })
  @DisplayName("Sizes breaking 2 <= min <= optimal <= max are rejected")
  void invalidSizes(int min, int optimal, int max) {
    // Act
    StudyMatchException e = assertThrows(StudyMatchException.class, () -> new GroupingRule(min, optimal, max, false));

    // Assert
    assertEquals(ErrorCode.INVALID_GROUP_SIZES, e.getCode());
  }

  @Test
  @DisplayName("Updating with valid sizes changes them")
  void updateSizesChangesThem() {
    // Act - teacher widens the range
    rule.updateSizes(2, 4, 6);

    // Assert
    assertEquals(2, rule.getMinSize());
    assertEquals(4, rule.getOptimalSize());
    assertEquals(6, rule.getMaxSize());
  }

  @Test
  @DisplayName("A failed update keeps the old sizes")
  void invalidUpdateKeepsOldSizes() {
    // Act
    assertThrows(StudyMatchException.class, () -> rule.updateSizes(5, 3, 4));

    // Assert - nothing half-applied
    assertEquals(2, rule.getMinSize());
    assertEquals(3, rule.getOptimalSize());
    assertEquals(4, rule.getMaxSize());
  }

  // separations (pairs that can't be in the same group)

  @Test
  @DisplayName("A pair of students can be separated")
  void addSeparation() {
    // Act
    rule.addSeparation(ana, bruno);

    // Assert
    assertEquals(1, rule.getSeparations().size());
  }

  @Test
  @DisplayName("A student can't be separated from themselves")
  void separationWithSameStudentIsRejected() {
    // Act
    StudyMatchException e = assertThrows(StudyMatchException.class, () -> rule.addSeparation(ana, ana));

    // Assert
    assertEquals(ErrorCode.SEPARATION_SAME_STUDENT, e.getCode());
  }

  @Test
  @DisplayName("The same pair can't be added twice")
  void duplicateSeparationIsRejected() {
    // Arrange
    rule.addSeparation(ana, bruno);

    // Act
    StudyMatchException e = assertThrows(StudyMatchException.class, () -> rule.addSeparation(ana, bruno));

    // Assert
    assertEquals(ErrorCode.SEPARATION_ALREADY_EXISTS, e.getCode());
  }

  @Test
  @DisplayName("The same pair in reverse order counts as a duplicate")
  void reversedSeparationIsRejected() {
    // Arrange
    rule.addSeparation(ana, bruno);

    // Act - (bruno, ana) is the same pair as (ana, bruno)
    StudyMatchException e = assertThrows(StudyMatchException.class, () -> rule.addSeparation(bruno, ana));

    // Assert
    assertEquals(ErrorCode.SEPARATION_ALREADY_EXISTS, e.getCode());
  }

}
