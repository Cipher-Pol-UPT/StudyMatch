package com.studymatch.models;

import java.time.LocalDateTime;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotNull;

@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = { "student_id", "subject_id" }))
public class TutorRole {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @OneToOne
  @NotNull
  private TutorApplication application;

  @ManyToOne
  @JoinColumn(name = "student_id")
  @NotNull
  private Student student;

  @ManyToOne
  @JoinColumn(name = "subject_id")
  @NotNull
  private Subject subject;

  protected TutorRole() {
  }

  TutorRole(TutorApplication application) {
    this.application = application;
    this.student = application.getStudent();
    this.subject = application.getSubject();
  }

  public Long getId() {
    return id;
  }

  public TutorApplication getApplication() {
    return application;
  }

  public Student getStudent() {
    return student;
  }

  public Subject getSubject() {
    return subject;
  }

  public LocalDateTime getApprovedAt() {
    return application.getDecidedAt();
  }

}
