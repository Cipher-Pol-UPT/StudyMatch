package com.studymatch.models;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.validation.constraints.NotNull;

@Entity
public class Separation {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne
  @NotNull
  private GroupingRule groupingRule;

  @ManyToOne
  @NotNull
  private Student firstStudent;

  @ManyToOne
  @NotNull
  private Student secondStudent;

  protected Separation() {
  }

  Separation(GroupingRule groupingRule, Student firstStudent, Student secondStudent) {
    this.groupingRule = groupingRule;
    this.firstStudent = firstStudent;
    this.secondStudent = secondStudent;
  }

  public Long getId() {
    return id;
  }

  public GroupingRule getGroupingRule() {
    return groupingRule;
  }

  public Student getFirstStudent() {
    return firstStudent;
  }

  public Student getSecondStudent() {
    return secondStudent;
  }

  boolean involves(Student a, Student b) {
    return (firstStudent.equals(a) && secondStudent.equals(b))
        || (firstStudent.equals(b) && secondStudent.equals(a));
  }

}
