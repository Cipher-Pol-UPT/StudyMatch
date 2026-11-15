package com.studymatch.utils;

// LIKE patterns for the search boxes, % and _ are wildcards in LIKE so they're escaped to be searched as normal characters
public final class SearchPatterns {

  public static final char ESCAPE = '\\';

  private SearchPatterns() {
  }

  // part of the text, any capitals (compare it with lower(column))
  public static String containing(String text) {
    String literal = text.toLowerCase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    return "%" + literal + "%";
  }

}
