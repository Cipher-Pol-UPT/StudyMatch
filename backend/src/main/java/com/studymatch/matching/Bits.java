package com.studymatch.matching;

// sets of students as bits, 64 students per long
final class Bits {

  private Bits() {
  }

  static int words(int students) {
    return (students + 63) / 64;
  }

  static void set(long[] bits, int student) {
    bits[student / 64] |= 1L << (student % 64);
  }

  static void clear(long[] bits, int student) {
    bits[student / 64] &= ~(1L << (student % 64));
  }

  // someone in both sets, not counting except (-1 for nobody)
  static boolean overlap(long[] first, long[] second, int except) {
    for (int word = 0; word < first.length; word++) {
      long shared = first[word] & second[word];
      if (except >= 0 && except / 64 == word) {
        shared &= ~(1L << (except % 64));
      }
      if (shared != 0) {
        return true;
      }
    }
    return false;
  }

}
