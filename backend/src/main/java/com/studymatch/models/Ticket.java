package com.studymatch.models;

import java.time.LocalDateTime;

import com.studymatch.enums.Role;
import com.studymatch.enums.TicketCategory;
import com.studymatch.enums.TicketStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// a request to the helpdesk, answered from its category's queue (#362, GR15)
@Entity
@Table(indexes = @Index(columnList = "author_number"))
public class Ticket {

  public static final int MAX_SUBJECT_LENGTH = 100;
  public static final int MAX_DESCRIPTION_LENGTH = 2000;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  // lazy, my tickets are always mine
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "author_number")
  @NotNull
  private User author;

  @NotNull
  @Enumerated(EnumType.STRING)
  private TicketCategory category;

  @NotNull
  @Size(max = MAX_SUBJECT_LENGTH)
  @Column(length = MAX_SUBJECT_LENGTH)
  private String subject;

  @NotNull
  @Size(max = MAX_DESCRIPTION_LENGTH)
  @Column(length = MAX_DESCRIPTION_LENGTH)
  private String description;

  @NotNull
  @Enumerated(EnumType.STRING)
  private TicketStatus status;

  @NotNull
  private LocalDateTime createdAt;

  // the last reply, so my tickets show the busiest first
  @NotNull
  private LocalDateTime updatedAt;

  protected Ticket() {
  }

  public Ticket(User author, TicketCategory category, String subject, String description) {
    this.author = author;
    this.category = category;
    this.subject = subject.strip();
    this.description = description.strip();
    this.status = TicketStatus.OPEN;
    this.createdAt = LocalDateTime.now();
    this.updatedAt = createdAt;
  }

  public Long getId() {
    return id;
  }

  public User getAuthor() {
    return author;
  }

  public TicketCategory getCategory() {
    return category;
  }

  public Role getQueue() {
    return category.queue();
  }

  public String getSubject() {
    return subject;
  }

  public String getDescription() {
    return description;
  }

  public TicketStatus getStatus() {
    return status;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public LocalDateTime getUpdatedAt() {
    return updatedAt;
  }

  // the author writing back on a resolved ticket means it isn't solved after all
  public void authorReplied() {
    if (status == TicketStatus.RESOLVED) {
      status = TicketStatus.OPEN;
    }
    updatedAt = LocalDateTime.now();
  }

}
