package com.studymatch.models;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

// one class of an edition, like T1 or PL2; students are enrolled in a class, and lessons belong to one
@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = { "subject_edition_id", "code" }))
public class ClassSection {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne
  @JoinColumn(name = "subject_edition_id")
  @NotNull
  private SubjectEdition subjectEdition;

  @NotBlank
  private String code;

  // null until Staff picks one, and a section without one can't be scheduled
  @ManyToOne
  @JoinColumn(name = "teacher_number")
  private Teacher teacher;

  protected ClassSection() {
  }

  ClassSection(SubjectEdition subjectEdition, String code) {
    this.subjectEdition = subjectEdition;
    this.code = code;
  }

  public Long getId() {
    return id;
  }

  public SubjectEdition getSubjectEdition() {
    return subjectEdition;
  }

  public String getCode() {
    return code;
  }

  public Teacher getTeacher() {
    return teacher;
  }

  // only through the edition, which checks the teacher is one of its own
  void setTeacher(Teacher teacher) {
    this.teacher = teacher;
  }

}
