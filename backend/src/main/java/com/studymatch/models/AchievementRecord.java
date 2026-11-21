package com.studymatch.models;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OrderBy;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

// a certificate with several achievements on one page, with its own code for the QR
@Entity
public class AchievementRecord {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne
  @JoinColumn(name = "student_id")
  @NotNull
  private Student student;

  @ManyToMany
  @OrderBy("earnedAt")
  private List<EarnedAchievement> achievements = new ArrayList<>();

  @NotNull
  private LocalDate issuedAt;

  @NotBlank
  @Column(unique = true)
  private String verificationCode;

  protected AchievementRecord() {
  }

  public AchievementRecord(Student student, List<EarnedAchievement> achievements) {
    this.student = student;
    this.achievements = new ArrayList<>(achievements);
    this.issuedAt = LocalDate.now();
    this.verificationCode = UUID.randomUUID().toString();
  }

  public Long getId() {
    return id;
  }

  public Student getStudent() {
    return student;
  }

  public List<EarnedAchievement> getAchievements() {
    return List.copyOf(achievements);
  }

  public LocalDate getIssuedAt() {
    return issuedAt;
  }

  public String getVerificationCode() {
    return verificationCode;
  }

}
