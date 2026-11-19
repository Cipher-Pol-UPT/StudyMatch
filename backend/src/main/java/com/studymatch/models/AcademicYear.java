package com.studymatch.models;

import java.time.LocalDate;
import java.time.Month;

import org.springframework.data.domain.Persistable;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Transient;

// the id is set by us, so without isNew() save() would merge an existing year instead of failing on the key
@Entity
public class AcademicYear implements Persistable<Integer> {

  public static final Month FIRST_MONTH = Month.SEPTEMBER;

  @Id
  private int startYear;

  @Transient
  private boolean isNew = true;

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

  @Override
  public Integer getId() {
    return startYear;
  }

  @Override
  public boolean isNew() {
    return isNew;
  }

  @PostLoad
  @PostPersist
  void markStored() {
    isNew = false;
  }

}
