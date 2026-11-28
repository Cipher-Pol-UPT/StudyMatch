package com.studymatch.models;

import java.time.LocalDate;
import java.time.Month;
import java.util.Map;
import java.util.stream.Stream;

import org.springframework.data.domain.Persistable;

import com.studymatch.exceptions.ErrorCode;
import com.studymatch.exceptions.StudyMatchException;

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

  // all four or none: years from before #334 have none, every year started or edited since has all four
  private LocalDate semester1Start;

  private LocalDate semester1End;

  private LocalDate semester2Start;

  private LocalDate semester2End;

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

  // 1 September to 31 August
  public LocalDate firstDay() {
    return LocalDate.of(startYear, FIRST_MONTH, 1);
  }

  public LocalDate lastDay() {
    return firstDay().plusYears(1).minusDays(1);
  }

  public boolean hasSemesters() {
    return semester1Start != null;
  }

  public LocalDate getSemester1Start() {
    return semester1Start;
  }

  public LocalDate getSemester1End() {
    return semester1End;
  }

  public LocalDate getSemester2Start() {
    return semester2Start;
  }

  public LocalDate getSemester2End() {
    return semester2End;
  }

  // checked in this order so several problems always give the same code: inside the year, each semester the
  // right way round, then semester 2 after semester 1. nothing changes if one fails
  public void setSemesters(LocalDate semester1Start, LocalDate semester1End, LocalDate semester2Start,
      LocalDate semester2End) {
    if (Stream.of(semester1Start, semester1End, semester2Start, semester2End).anyMatch(this::outside)) {
      throw new StudyMatchException(ErrorCode.SEMESTER_OUTSIDE_YEAR,
          Map.of("from", firstDay().toString(), "to", lastDay().toString()));
    }
    if (!semester1End.isAfter(semester1Start)) {
      throw new StudyMatchException(ErrorCode.SEMESTER_ENDS_BEFORE_START, Map.of("semester", 1));
    }
    if (!semester2End.isAfter(semester2Start)) {
      throw new StudyMatchException(ErrorCode.SEMESTER_ENDS_BEFORE_START, Map.of("semester", 2));
    }
    if (!semester2Start.isAfter(semester1End)) {
      throw new StudyMatchException(ErrorCode.SEMESTERS_OVERLAP);
    }
    this.semester1Start = semester1Start;
    this.semester1End = semester1End;
    this.semester2Start = semester2Start;
    this.semester2End = semester2End;
  }

  // 1 or 2, or 0 when the date is in neither, like a holiday between them or a year with no dates
  public int semesterOf(LocalDate date) {
    if (!hasSemesters()) {
      return 0;
    }
    if (!date.isBefore(semester1Start) && !date.isAfter(semester1End)) {
      return 1;
    }
    if (!date.isBefore(semester2Start) && !date.isAfter(semester2End)) {
      return 2;
    }
    return 0;
  }

  private boolean outside(LocalDate date) {
    return date.isBefore(firstDay()) || date.isAfter(lastDay());
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
