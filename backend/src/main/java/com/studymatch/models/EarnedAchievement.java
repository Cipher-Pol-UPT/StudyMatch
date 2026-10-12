package com.studymatch.models;

import java.time.LocalDateTime;
import java.util.UUID;

import com.studymatch.enums.Achievement;

import jakarta.persistence.Column;
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
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = { "student_id", "achievement" }))
public class EarnedAchievement {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne
  @JoinColumn(name = "student_id")
  @NotNull
  private Student student;

  @Enumerated(EnumType.STRING)
  @Column(name = "achievement")
  @NotNull
  private Achievement achievement;

  @NotNull
  private LocalDateTime earnedAt;

  @NotBlank
  @Column(unique = true)
  private String verificationCode;

  protected EarnedAchievement() {
  }

  public EarnedAchievement(Student student, Achievement achievement) {
    this.student = student;
    this.achievement = achievement;
    this.earnedAt = LocalDateTime.now();
    this.verificationCode = UUID.randomUUID().toString();
  }

  public Long getId() {
    return id;
  }

  public Student getStudent() {
    return student;
  }

  public Achievement getAchievement() {
    return achievement;
  }

  public LocalDateTime getEarnedAt() {
    return earnedAt;
  }

  public String getVerificationCode() {
    return verificationCode;
  }

}
