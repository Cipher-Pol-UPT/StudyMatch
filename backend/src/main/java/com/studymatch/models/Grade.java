package com.studymatch.models;

import com.studymatch.exceptions.ErrorCode;
import com.studymatch.exceptions.StudyMatchException;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = { "enrolment_id", "assignment_id" }))
public class Grade {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne
  @JoinColumn(name = "enrolment_id")
  @NotNull
  private Enrolment enrolment;

  @ManyToOne
  @JoinColumn(name = "assignment_id")
  @NotNull
  private Assignment assignment;

  @DecimalMin("0.0")
  @DecimalMax("20.0")
  private double value;

  protected Grade() {
  }

  public Grade(Enrolment enrolment, Assignment assignment, double value) {
    this.enrolment = enrolment;
    this.assignment = assignment;
    this.value = checkValue(value);
  }

  // grades are 0 to 20, the annotations only see it when it's saved
  private static double checkValue(double value) {
    if (value < 0 || value > 20) {
      throw new StudyMatchException(ErrorCode.GRADE_OUT_OF_RANGE);
    }
    return value;
  }

  public Long getId() {
    return id;
  }

  public Enrolment getEnrolment() {
    return enrolment;
  }

  public Assignment getAssignment() {
    return assignment;
  }

  public double getValue() {
    return value;
  }

  public void setValue(double value) {
    this.value = checkValue(value);
  }

}
