package com.studymatch.models;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity
public class AcademicYear {

  @Id
  private int startYear;

  protected AcademicYear() {
  }

  public AcademicYear(int startYear) {
    this.startYear = startYear;
  }

  public int getStartYear() {
    return startYear;
  }

}
