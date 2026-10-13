package com.studymatch.models;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.studymatch.enums.AssignmentType;
import com.studymatch.exceptions.ErrorCode;
import com.studymatch.exceptions.StudyMatchException;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Entity
public class Assignment {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne
  @NotNull
  private SubjectEdition subjectEdition;

  @NotBlank
  private String name;

  @Enumerated(EnumType.STRING)
  @NotNull
  private AssignmentType type;

  @NotNull
  private LocalDate date;

  @NotNull
  @DecimalMin("0.01")
  @DecimalMax("1.00")
  @Digits(integer = 1, fraction = 2)
  @Column(precision = 3, scale = 2)
  private BigDecimal weight;

  @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true)
  private GroupingRule groupingRule;

  protected Assignment() {
  }

  public Assignment(SubjectEdition subjectEdition, String name, AssignmentType type, LocalDate date,
      BigDecimal weight, GroupingRule groupingRule) {
    if (groupingRule != null && type != AssignmentType.PROJECT) {
      throw new StudyMatchException(ErrorCode.GROUP_WORK_NOT_ALLOWED_FOR_EXAMS);
    }
    this.subjectEdition = subjectEdition;
    this.name = name;
    this.type = type;
    this.date = date;
    this.weight = weight;
    this.groupingRule = groupingRule;
  }

  public Long getId() {
    return id;
  }

  public SubjectEdition getSubjectEdition() {
    return subjectEdition;
  }

  public String getName() {
    return name;
  }

  public AssignmentType getType() {
    return type;
  }

  public LocalDate getDate() {
    return date;
  }

  public BigDecimal getWeight() {
    return weight;
  }

  public GroupingRule getGroupingRule() {
    return groupingRule;
  }

}
