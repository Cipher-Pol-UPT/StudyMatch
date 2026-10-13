package com.studymatch.models;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotNull;

@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = { "lesson_id", "enrolment_id" }))
public class Absence {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne
  @JoinColumn(name = "lesson_id")
  @NotNull
  private Lesson lesson;

  @ManyToOne
  @JoinColumn(name = "enrolment_id")
  @NotNull
  private Enrolment enrolment;

  private boolean justified;

  protected Absence() {
  }

  public Absence(Lesson lesson, Enrolment enrolment, boolean justified) {
    this.lesson = lesson;
    this.enrolment = enrolment;
    this.justified = justified;
  }

  public Long getId() {
    return id;
  }

  public Lesson getLesson() {
    return lesson;
  }

  public Enrolment getEnrolment() {
    return enrolment;
  }

  public boolean isJustified() {
    return justified;
  }

  public void setJustified(boolean justified) {
    this.justified = justified;
  }

}
