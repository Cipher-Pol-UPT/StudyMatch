package com.studymatch.models;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.studymatch.enums.RoomType;

class RoomTest {

  private static Room room(String building, int floor, int x, int y, int width, int depth) {
    return new Room("R", building, floor, RoomType.CLASSROOM, 30, x, y, width, depth);
  }

  // the other room is (10,10) 10 x 10 on Building A, floor 1

  @ParameterizedTest(name = "({0},{1}) {2} x {3} overlaps")
  @CsvSource({
      "10, 10, 10, 10", // same place
      "5, 5, 10, 10", // over its top-left corner
      "19, 19, 5, 5", // one cell over its bottom-right corner
      "12, 12, 2, 2", // inside it
      "0, 0, 50, 50" // around it
  })
  @DisplayName("Rooms that share at least one cell on the same floor overlap")
  void overlapping(int x, int y, int width, int depth) {
    // Arrange
    Room other = room("Building A", 1, 10, 10, 10, 10);

    // Act + Assert
    assertTrue(room("Building A", 1, x, y, width, depth).overlaps(other));
    assertTrue(other.overlaps(room("Building A", 1, x, y, width, depth)));
  }

  @ParameterizedTest(name = "({0},{1}) {2} x {3} doesn't overlap")
  @CsvSource({
      "0, 10, 10, 10", // touching its left edge
      "20, 10, 10, 10", // touching its right edge
      "10, 0, 10, 10", // touching its top edge
      "10, 20, 10, 10", // touching its bottom edge
      "20, 20, 5, 5" // touching only its corner
  })
  @DisplayName("Sharing an edge or a corner isn't overlapping (boundary)")
  void touching(int x, int y, int width, int depth) {
    // Arrange
    Room other = room("Building A", 1, 10, 10, 10, 10);

    // Act + Assert
    assertFalse(room("Building A", 1, x, y, width, depth).overlaps(other));
  }

  @Test
  @DisplayName("The same position on another floor or in another building isn't an overlap")
  void otherFloor() {
    // Arrange
    Room other = room("Building A", 1, 10, 10, 10, 10);

    // Act + Assert
    assertFalse(room("Building A", 2, 10, 10, 10, 10).overlaps(other));
    assertFalse(room("Building B", 1, 10, 10, 10, 10).overlaps(other));
  }

  @Test
  @DisplayName("The building is the same whatever its case")
  void buildingCase() {
    // Arrange
    Room other = room("Building A", 1, 10, 10, 10, 10);

    // Act + Assert
    assertTrue(room("BUILDING a", 1, 10, 10, 10, 10).overlaps(other));
    assertEquals(other.floorKey(), room("BUILDING a", 1, 0, 0, 1, 1).floorKey());
  }

  // the grid is 100 x 100: ending at 100 is the last cell, 101 is off it

  @ParameterizedTest(name = "({0},{1}) {2} x {3} fits")
  @CsvSource({
      "0, 0, 100, 100",
      "99, 99, 1, 1",
      "60, 0, 40, 10"
  })
  @DisplayName("A room ending exactly at the edge of the grid fits (boundary)")
  void fits(int x, int y, int width, int depth) {
    assertTrue(room("Building A", 1, x, y, width, depth).fitsOnFloor());
  }

  @ParameterizedTest(name = "({0},{1}) {2} x {3} doesn't fit")
  @CsvSource({
      "1, 0, 100, 10",
      "0, 1, 10, 100",
      "99, 99, 2, 1",
      "61, 0, 40, 10"
  })
  @DisplayName("One cell past the edge of the grid doesn't fit (boundary)")
  void doesNotFit(int x, int y, int width, int depth) {
    assertFalse(room("Building A", 1, x, y, width, depth).fitsOnFloor());
  }

  @Test
  @DisplayName("A new room is active")
  void newRoomIsActive() {
    assertTrue(room("Building A", 1, 0, 0, 10, 10).isActive());
  }

}
