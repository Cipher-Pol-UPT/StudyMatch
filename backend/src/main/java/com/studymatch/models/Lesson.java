package com.studymatch.models;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;

@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = { "class_section_id", "starts_at" }))
public class Lesson {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne
  @JoinColumn(name = "class_section_id")
  @NotNull
  private ClassSection classSection;

  @NotNull
  @PastOrPresent
  @Column(name = "starts_at")
  private LocalDateTime startsAt;

  protected Lesson() {
  }

  public Lesson(ClassSection classSection, LocalDateTime startsAt) {
    this.classSection = classSection;
    this.startsAt = startsAt;
  }

  public Long getId() {
    return id;
  }

  public ClassSection getClassSection() {
    return classSection;
  }

  public LocalDateTime getStartsAt() {
    return startsAt;
  }

}
