package com.studymatch.utils;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PresenceTrackerTest {

  private PresenceTracker tracker;

  @BeforeEach
  void setUp() {
    tracker = new PresenceTracker();
  }

  @Test
  @DisplayName("The first tab puts the user online")
  void firstTabIsFirst() {
    // Act
    boolean first = tracker.connect(1L, "a");

    // Assert
    assertTrue(first);
    assertTrue(tracker.isOnline(1L));
  }

  @Test
  @DisplayName("A second tab isn't the first")
  void secondTabIsNotFirst() {
    // Arrange
    tracker.connect(1L, "a");

    // Act
    boolean first = tracker.connect(1L, "b");

    // Assert
    assertFalse(first);
  }

  @Test
  @DisplayName("Closing one of two tabs keeps the user online")
  void closingOneOfTwoTabs() {
    // Arrange
    tracker.connect(1L, "a");
    tracker.connect(1L, "b");

    // Act
    boolean last = tracker.disconnect(1L, "a");

    // Assert
    assertFalse(last);
    assertTrue(tracker.isOnline(1L));
  }

  @Test
  @DisplayName("Closing the last tab puts the user offline")
  void closingTheLastTab() {
    // Arrange
    tracker.connect(1L, "a");

    // Act
    boolean last = tracker.disconnect(1L, "a");

    // Assert
    assertTrue(last);
    assertFalse(tracker.isOnline(1L));
  }

  @Test
  @DisplayName("A session closing twice only counts once")
  void closingTwice() {
    // Arrange
    tracker.connect(1L, "a");
    tracker.disconnect(1L, "a");

    // Act
    boolean last = tracker.disconnect(1L, "a");

    // Assert
    assertFalse(last);
  }

  @Test
  @DisplayName("Closing a session that was never opened changes nothing")
  void closingAnUnknownSession() {
    // Arrange
    tracker.connect(1L, "a");

    // Act
    boolean last = tracker.disconnect(1L, "b");

    // Assert
    assertFalse(last);
    assertTrue(tracker.isOnline(1L));
  }

  @Test
  @DisplayName("Someone who never connected is offline, and another user's tabs don't count for them")
  void othersAreOffline() {
    // Arrange
    tracker.connect(1L, "a");

    // Act + Assert
    assertFalse(tracker.isOnline(2L));
    assertFalse(tracker.disconnect(2L, "a"));
    assertTrue(tracker.isOnline(1L));
  }

}
