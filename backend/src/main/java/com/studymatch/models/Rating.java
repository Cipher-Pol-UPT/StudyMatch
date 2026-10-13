package com.studymatch.models;

import java.time.LocalDateTime;

import com.studymatch.exceptions.ErrorCode;
import com.studymatch.exceptions.StudyMatchException;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Entity
public class Rating {

  public static final int RATING_WINDOW_DAYS = 7;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @OneToOne
  @JoinColumn(unique = true)
  @NotNull
  private Participation participation;

  @Min(1)
  @Max(5)
  private int stars;

  @Size(max = 500)
  private String comment;

  @NotNull
  private LocalDateTime ratedAt;

  protected Rating() {
  }

  public Rating(Participation participation, int stars, String comment) {
    if (!participation.isAttended()) {
      throw new StudyMatchException(ErrorCode.ATTENDANCE_NOT_CONFIRMED);
    }
    LocalDateTime now = LocalDateTime.now();
    if (now.isAfter(participation.getStudyRoom().getEndsAt().plusDays(RATING_WINDOW_DAYS))) {
      throw new StudyMatchException(ErrorCode.RATING_WINDOW_CLOSED);
    }
    this.participation = participation;
    this.stars = stars;
    this.comment = comment;
    this.ratedAt = now;
  }

  public Long getId() {
    return id;
  }

  public Participation getParticipation() {
    return participation;
  }

  public TutorRole getTutorRole() {
    return participation.getStudyRoom().getTutorRole();
  }

  public int getStars() {
    return stars;
  }

  public String getComment() {
    return comment;
  }

  public LocalDateTime getRatedAt() {
    return ratedAt;
  }

}
