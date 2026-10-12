package com.studymatch.models;

import java.time.LocalDateTime;

import com.studymatch.enums.TutorApplicationStatus;
import com.studymatch.exceptions.ErrorCode;
import com.studymatch.exceptions.StudyMatchException;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Entity
public class TutorApplication {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne
  @NotNull
  private Student student;

  @ManyToOne
  @NotNull
  private Subject subject;

  @Size(max = 500)
  private String motivation;

  @Enumerated(EnumType.STRING)
  @NotNull
  private TutorApplicationStatus status = TutorApplicationStatus.PENDING;

  @NotNull
  private LocalDateTime submittedAt;

  private LocalDateTime decidedAt;

  @ManyToOne
  private Teacher decidedBy;

  @Size(max = 500)
  private String rejectionReason;

  protected TutorApplication() {
  }

  public TutorApplication(Student student, Subject subject, String motivation) {
    this.student = student;
    this.subject = subject;
    this.motivation = motivation;
    this.submittedAt = LocalDateTime.now();
  }

  public Long getId() {
    return id;
  }

  public Student getStudent() {
    return student;
  }

  public Subject getSubject() {
    return subject;
  }

  public String getMotivation() {
    return motivation;
  }

  public TutorApplicationStatus getStatus() {
    return status;
  }

  public LocalDateTime getSubmittedAt() {
    return submittedAt;
  }

  public LocalDateTime getDecidedAt() {
    return decidedAt;
  }

  public Teacher getDecidedBy() {
    return decidedBy;
  }

  public String getRejectionReason() {
    return rejectionReason;
  }

  public TutorRole approve(Teacher teacher) {
    decide(teacher, TutorApplicationStatus.APPROVED);
    return new TutorRole(this);
  }

  public void reject(Teacher teacher, String rejectionReason) {
    decide(teacher, TutorApplicationStatus.REJECTED);
    this.rejectionReason = rejectionReason;
  }

  private void decide(Teacher teacher, TutorApplicationStatus status) {
    if (this.status != TutorApplicationStatus.PENDING) {
      throw new StudyMatchException(ErrorCode.APPLICATION_ALREADY_DECIDED);
    }
    this.status = status;
    this.decidedBy = teacher;
    this.decidedAt = LocalDateTime.now();
  }

}
