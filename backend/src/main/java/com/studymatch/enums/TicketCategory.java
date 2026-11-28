package com.studymatch.enums;

// the category picks the queue: Account goes to Admin, the rest to Staff (GR15)
public enum TicketCategory {
  ACCOUNT,
  GRADES_AND_ENROLMENT,
  TECHNICAL,
  OTHER;

  public Role queue() {
    return this == ACCOUNT ? Role.ADMIN : Role.STAFF;
  }
}
