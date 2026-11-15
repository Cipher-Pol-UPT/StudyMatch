package com.studymatch.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SearchPatternsTest {

  @Test
  @DisplayName("The text is lowercased and can be anywhere in the column")
  void containingIsLowercaseAndWrapped() {
    // Act + Assert
    assertEquals("%ana test%", SearchPatterns.containing("Ana Test"));
  }

  @Test
  @DisplayName("% and _ are escaped so they're searched as normal characters")
  void wildcardsAreEscaped() {
    // Act + Assert
    assertEquals("%50\\%\\_off%", SearchPatterns.containing("50%_off"));
  }

  @Test
  @DisplayName("The escape character itself is escaped first, so it isn't doubled up")
  void backslashIsEscaped() {
    // Act + Assert
    assertEquals("%a\\\\\\%%", SearchPatterns.containing("a\\%"));
  }

}
