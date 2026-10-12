package com.studymatch.models;

import com.studymatch.enums.EnrolmentStatus;
import com.studymatch.exceptions.ErrorCode;
import com.studymatch.exceptions.StudyMatchException;

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
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = { "student_id", "subject_edition_id" }))
public class Enrolment {

  public static final int PASSING_GRADE = 10;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne
  @JoinColumn(name = "student_id")
  @NotNull
  private Student student;

  @ManyToOne
  @NotNull
  private ClassSection classSection;

  @ManyToOne
  @JoinColumn(name = "subject_edition_id")
  @NotNull
  private SubjectEdition subjectEdition;

  @Enumerated(EnumType.STRING)
  @NotNull
  private EnrolmentStatus status = EnrolmentStatus.IN_PROGRESS;

  @Min(0)
  @Max(20)
  private Integer finalGrade;

  protected Enrolment() {
  }

  public Enrolment(Student student, ClassSection classSection) {
    this.student = student;
    this.classSection = classSection;
    this.subjectEdition = classSection.getSubjectEdition();
  }

  public Long getId() {
    return id;
  }

  public Student getStudent() {
    return student;
  }

  public ClassSection getClassSection() {
    return classSection;
  }

  public void changeClassSection(ClassSection classSection) {
    if (!classSection.getSubjectEdition().equals(subjectEdition)) {
      throw new StudyMatchException(ErrorCode.CLASS_SECTION_NOT_IN_SUBJECT_EDITION);
    }
    this.classSection = classSection;
  }

  public SubjectEdition getSubjectEdition() {
    return subjectEdition;
  }

  public EnrolmentStatus getStatus() {
    return status;
  }

  public Integer getFinalGrade() {
    return finalGrade;
  }

  public void setFinalGrade(int finalGrade) {
    this.finalGrade = finalGrade;
    this.status = finalGrade >= PASSING_GRADE ? EnrolmentStatus.PASSED : EnrolmentStatus.FAILED;
  }

}
