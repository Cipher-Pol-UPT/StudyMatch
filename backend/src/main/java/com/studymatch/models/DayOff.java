package com.studymatch.models;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// a day or a run of days without classes in an academic year, like a holiday or the exam season (#336).
// one day has the same first and last day
@Entity
public class DayOff {

  public static final int REASON_MAX_LENGTH = 100;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "academic_year")
  @NotNull
  private AcademicYear academicYear;

  @NotNull
  @Column(name = "first_day")
  private LocalDate firstDay;

  @NotNull
  @Column(name = "last_day")
  private LocalDate lastDay;

  @NotBlank
  @Size(max = REASON_MAX_LENGTH)
  private String reason;

  protected DayOff() {
  }

  public DayOff(AcademicYear academicYear, LocalDate firstDay, LocalDate lastDay, String reason) {
    this.academicYear = academicYear;
    this.firstDay = firstDay;
    this.lastDay = lastDay;
    this.reason = reason;
  }

  public Long getId() {
    return id;
  }

  public AcademicYear getAcademicYear() {
    return academicYear;
  }

  public LocalDate getFirstDay() {
    return firstDay;
  }

  public LocalDate getLastDay() {
    return lastDay;
  }

  public String getReason() {
    return reason;
  }

  public boolean includes(LocalDate date) {
    return !date.isBefore(firstDay) && !date.isAfter(lastDay);
  }

}
