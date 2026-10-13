package com.studymatch.models;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = { "degree_id", "subject_id" }))
public class DegreeSubject {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne
  @NotNull
  private Degree degree;

  @ManyToOne
  @NotNull
  private Subject subject;

  @Min(1)
  private int curricularYear;

  @Min(1)
  @Max(2)
  private int semester;

  protected DegreeSubject() {
  }

  DegreeSubject(Subject subject, Degree degree, int curricularYear, int semester) {
    this.subject = subject;
    this.degree = degree;
    this.curricularYear = curricularYear;
    this.semester = semester;
  }

  public Long getId() {
    return id;
  }

  public Degree getDegree() {
    return degree;
  }

  public Subject getSubject() {
    return subject;
  }

  public int getCurricularYear() {
    return curricularYear;
  }

  public void setCurricularYear(int curricularYear) {
    this.curricularYear = curricularYear;
  }

  public int getSemester() {
    return semester;
  }

  public void setSemester(int semester) {
    this.semester = semester;
  }

}
