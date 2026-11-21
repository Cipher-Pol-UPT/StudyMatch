package com.studymatch.matching;

// the students can't be split within the group sizes; its own type so a real bug isn't mistaken for this
public class NoValidSplitException extends IllegalArgumentException {

  public NoValidSplitException(int students) {
    super("no valid split for " + students + " students");
  }

}
