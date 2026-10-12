package com.studymatch.models;

import java.time.LocalDateTime;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotNull;

@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = { "study_room_id", "student_id" }))
public class Participation {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne
  @JoinColumn(name = "study_room_id")
  @NotNull
  private StudyRoom studyRoom;

  @ManyToOne
  @JoinColumn(name = "student_id")
  @NotNull
  private Student student;

  @NotNull
  private LocalDateTime joinedAt;

  private boolean attended;

  protected Participation() {
  }

  Participation(StudyRoom studyRoom, Student student) {
    this.studyRoom = studyRoom;
    this.student = student;
    this.joinedAt = LocalDateTime.now();
  }

  public Long getId() {
    return id;
  }

  public StudyRoom getStudyRoom() {
    return studyRoom;
  }

  public Student getStudent() {
    return student;
  }

  public LocalDateTime getJoinedAt() {
    return joinedAt;
  }

  public boolean isAttended() {
    return attended;
  }

  void setAttended(boolean attended) {
    this.attended = attended;
  }

}
