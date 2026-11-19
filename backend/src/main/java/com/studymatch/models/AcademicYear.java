package com.studymatch.models;

import java.time.LocalDate;
import java.time.Month;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity
public class AcademicYear {

  public static final Month FIRST_MONTH = Month.SEPTEMBER;

  @Id
  private int startYear;

  protected AcademicYear() {
  }

  public AcademicYear(int startYear) {
    this.startYear = startYear;
  }

  // the academic year a date falls in: September onwards is this year's, January to August last year's
  public static int startYearOf(LocalDate date) {
    return date.getMonth().compareTo(FIRST_MONTH) >= 0 ? date.getYear() : date.getYear() - 1;
  }

  public int getStartYear() {
    return startYear;
  }

  public AcademicYear next() {
    return new AcademicYear(startYear + 1);
  }

}
