package com.studymatch.matching;

// the separations leave no valid grouping; its own type so a real bug isn't mistaken for this
public class UnmetSeparationsException extends IllegalStateException {

  public UnmetSeparationsException() {
    super("the separations leave no valid grouping");
  }

}
