package com.studymatch.models;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// one message in a ticket's thread, from its author or the queue answering it (#364)
@Entity
@Table(indexes = @Index(columnList = "ticket_id, id"))
public class TicketReply {

  public static final int MAX_TEXT_LENGTH = 2000;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  // lazy, the thread is always read through its ticket
  @ManyToOne(fetch = FetchType.LAZY)
  @NotNull
  private Ticket ticket;

  // the thread shows who wrote each reply
  @ManyToOne
  @NotNull
  private User author;

  @NotNull
  @Size(max = MAX_TEXT_LENGTH)
  @Column(length = MAX_TEXT_LENGTH)
  private String text;

  @NotNull
  private LocalDateTime createdAt;

  protected TicketReply() {
  }

  public TicketReply(Ticket ticket, User author, String text) {
    this.ticket = ticket;
    this.author = author;
    this.text = text.strip();
    this.createdAt = LocalDateTime.now();
  }

  public Long getId() {
    return id;
  }

  public Ticket getTicket() {
    return ticket;
  }

  public User getAuthor() {
    return author;
  }

  public String getText() {
    return text;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

}
