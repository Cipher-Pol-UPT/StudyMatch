package com.studymatch.models;

import java.time.DayOfWeek;
import java.time.LocalTime;

import com.studymatch.exceptions.ErrorCode;
import com.studymatch.exceptions.StudyMatchException;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotNull;

// one 30-minute slot of the week grid (GR13) the teacher can't teach, every week of the year
@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = { "teacher_number", "academic_year", "day",
    "start_time" }))
public class UnavailableSlot {

  public static final int SLOT_MINUTES = 30;
  public static final LocalTime DAY_START = LocalTime.of(8, 0);
  public static final LocalTime DAY_END = LocalTime.of(20, 0);

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne
  @JoinColumn(name = "teacher_number")
  @NotNull
  private Teacher teacher;

  @ManyToOne
  @JoinColumn(name = "academic_year")
  @NotNull
  private AcademicYear academicYear;

  @Enumerated(EnumType.STRING)
  @NotNull
  private DayOfWeek day;

  @Column(name = "start_time")
  @NotNull
  private LocalTime start;

  protected UnavailableSlot() {
  }

  public UnavailableSlot(Teacher teacher, AcademicYear academicYear, DayOfWeek day, LocalTime start) {
    if (!fitsGrid(day, start)) {
      throw new StudyMatchException(ErrorCode.INVALID_SLOT);
    }
    this.teacher = teacher;
    this.academicYear = academicYear;
    this.day = day;
    this.start = start;
  }

  // Monday to Friday, starting on the hour or half hour, and over by 20:00
  private static boolean fitsGrid(DayOfWeek day, LocalTime start) {
    return day.compareTo(DayOfWeek.FRIDAY) <= 0
        && !start.isBefore(DAY_START)
        && start.isBefore(DAY_END)
        && start.getMinute() % SLOT_MINUTES == 0
        && start.getSecond() == 0
        && start.getNano() == 0;
  }

  public Long getId() {
    return id;
  }

  public Teacher getTeacher() {
    return teacher;
  }

  public AcademicYear getAcademicYear() {
    return academicYear;
  }

  public DayOfWeek getDay() {
    return day;
  }

  public LocalTime getStart() {
    return start;
  }

}
