package com.studymatch.models;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import com.studymatch.exceptions.ErrorCode;
import com.studymatch.exceptions.StudyMatchException;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotNull;

@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = { "subject_id", "academic_year" }))
public class SubjectEdition {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne
  @JoinColumn(name = "subject_id")
  @NotNull
  private Subject subject;

  @ManyToOne
  @JoinColumn(name = "academic_year")
  @NotNull
  private AcademicYear academicYear;

  @ManyToMany
  private Set<Teacher> teachers = new HashSet<>();

  @OneToMany(mappedBy = "subjectEdition", cascade = CascadeType.ALL, orphanRemoval = true)
  private Set<ClassSection> classSections = new HashSet<>();

  protected SubjectEdition() {
  }

  public SubjectEdition(Subject subject, AcademicYear academicYear, Set<Teacher> teachers) {
    this.subject = subject;
    this.academicYear = academicYear;
    setTeachers(teachers);
  }

  public Long getId() {
    return id;
  }

  public Subject getSubject() {
    return subject;
  }

  public AcademicYear getAcademicYear() {
    return academicYear;
  }

  public Set<Teacher> getTeachers() {
    return Collections.unmodifiableSet(teachers);
  }

  public void setTeachers(Set<Teacher> teachers) {
    if (teachers.isEmpty()) {
      throw new StudyMatchException(ErrorCode.AT_LEAST_ONE_TEACHER_REQUIRED);
    }
    this.teachers.clear();
    this.teachers.addAll(teachers);
  }

  public Set<ClassSection> getClassSections() {
    return Collections.unmodifiableSet(classSections);
  }

  public void addClassSection(String code) {
    if (classSections.stream().anyMatch(cs -> cs.getCode().equals(code))) {
      throw new StudyMatchException(ErrorCode.CLASS_SECTION_ALREADY_EXISTS);
    }
    classSections.add(new ClassSection(this, code));
  }

}
