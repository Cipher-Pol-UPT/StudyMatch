package com.studymatch.models;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;

@Entity
public class PersonalityProfile {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @DecimalMin("0.0")
  @DecimalMax("1.0")
  private double leadership;

  @DecimalMin("0.0")
  @DecimalMax("1.0")
  private double problemSolving;

  @DecimalMin("0.0")
  @DecimalMax("1.0")
  private double creativity;

  @DecimalMin("0.0")
  @DecimalMax("1.0")
  private double execution;

  protected PersonalityProfile() {
  }

  public PersonalityProfile(double leadership, double problemSolving, double creativity, double execution) {
    this.leadership = leadership;
    this.problemSolving = problemSolving;
    this.creativity = creativity;
    this.execution = execution;
  }

  public Long getId() {
    return id;
  }

  public double getLeadership() {
    return leadership;
  }

  public void setLeadership(double leadership) {
    this.leadership = leadership;
  }

  public double getProblemSolving() {
    return problemSolving;
  }

  public void setProblemSolving(double problemSolving) {
    this.problemSolving = problemSolving;
  }

  public double getCreativity() {
    return creativity;
  }

  public void setCreativity(double creativity) {
    this.creativity = creativity;
  }

  public double getExecution() {
    return execution;
  }

  public void setExecution(double execution) {
    this.execution = execution;
  }

}
