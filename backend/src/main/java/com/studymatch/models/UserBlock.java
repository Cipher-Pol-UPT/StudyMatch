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

// one user blocking another's DMs, one row per direction (GR9). not the admin's account block
@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = { "blocker_number", "blocked_number" }))
public class UserBlock {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne
  @JoinColumn(name = "blocker_number")
  @NotNull
  private User blocker;

  @ManyToOne
  @JoinColumn(name = "blocked_number")
  @NotNull
  private User blocked;

  @NotNull
  private LocalDateTime createdAt;

  protected UserBlock() {
  }

  public UserBlock(User blocker, User blocked) {
    this.blocker = blocker;
    this.blocked = blocked;
    this.createdAt = LocalDateTime.now();
  }

  public Long getId() {
    return id;
  }

  public User getBlocker() {
    return blocker;
  }

  public User getBlocked() {
    return blocked;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

}
