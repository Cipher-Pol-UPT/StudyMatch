package com.studymatch.matching;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GroupingTest {

  private Grouping grouping;

  @BeforeEach
  void setUp() {
    // students 0-2 in group 0, 3-4 in group 1
    grouping = new Grouping(new int[] { 0, 0, 0, 1, 1 }, 2);
  }

  @Test
  @DisplayName("Sizes and members come from the groups given")
  void sizesAndMembers() {
    // Assert
    assertEquals(3, grouping.size(0));
    assertEquals(2, grouping.size(1));
    assertEquals(List.of(3, 4), grouping.members(1));
  }

  @Test
  @DisplayName("Moving a student updates both sizes")
  void moveUpdatesSizes() {
    // Act
    grouping.move(0, 1);

    // Assert
    assertEquals(1, grouping.groupOf(0));
    assertEquals(2, grouping.size(0));
    assertEquals(3, grouping.size(1));
  }

  @Test
  @DisplayName("Swapping two students keeps the sizes")
  void swapKeepsSizes() {
    // Act
    grouping.swap(0, 3);

    // Assert
    assertEquals(1, grouping.groupOf(0));
    assertEquals(0, grouping.groupOf(3));
    assertEquals(3, grouping.size(0));
    assertEquals(2, grouping.size(1));
  }

  @Test
  @DisplayName("Changing a copy leaves the original as it was")
  void copyIsIndependent() {
    // Arrange
    Grouping copy = grouping.copy();

    // Act
    copy.move(0, 1);

    // Assert
    assertEquals(0, grouping.groupOf(0));
    assertEquals(3, grouping.size(0));
  }

}
