package com.studymatch.enums;

import java.util.Arrays;
import java.util.List;

// the category picks the queue: Account goes to Admin, the rest to Staff (GR15)
public enum TicketCategory {
  ACCOUNT,
  GRADES_AND_ENROLMENT,
  TECHNICAL,
  OTHER;

  public Role queue() {
    return this == ACCOUNT ? Role.ADMIN : Role.STAFF;
  }

  // what one queue answers, empty for a role that answers none (#364)
  public static List<TicketCategory> inQueue(Role role) {
    return Arrays.stream(values()).filter(category -> category.queue() == role).toList();
  }
}
