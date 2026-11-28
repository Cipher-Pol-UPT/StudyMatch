package com.studymatch.models;

import java.time.LocalDateTime;

import com.studymatch.enums.Role;
import com.studymatch.enums.TicketCategory;
import com.studymatch.enums.TicketStatus;
import com.studymatch.exceptions.ErrorCode;
import com.studymatch.exceptions.StudyMatchException;

import jakarta.persistence.CheckConstraint;
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

// a request to the helpdesk, answered from its category's queue (#362, GR15).
// a guest who can't log in has an email and a private link instead of an author (#366)
@Entity
@Table(indexes = { @Index(columnList = "author_number"), @Index(columnList = "guest_email, created_at"),
    @Index(columnList = "guest_ip, created_at") },
    check = @CheckConstraint(name = "ticket_author_or_guest",
        constraint = "(author_number is null) <> (guest_email is null)"))
public class Ticket {

  public static final int MAX_SUBJECT_LENGTH = 100;
  public static final int MAX_DESCRIPTION_LENGTH = 2000;
  public static final int MAX_EMAIL_LENGTH = 254;
  // a campus can share one ip, so it gets more than one email
  public static final int MAX_GUEST_TICKETS_PER_EMAIL = 3;
  public static final int MAX_GUEST_TICKETS_PER_IP = 10;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  // lazy, my tickets are always mine. null for a guest
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "author_number")
  private User author;

  // where the link goes, null when there's an author
  @Size(max = MAX_EMAIL_LENGTH)
  @Column(length = MAX_EMAIL_LENGTH)
  private String guestEmail;

  // sha-256 of the link's token, the token itself is only in the email
  @Column(length = 64, unique = true)
  private String guestTokenHash;

  // only to count a guest's tickets in the last hour
  @Column(length = 45)
  private String guestIp;

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

  public static Ticket forGuest(String email, String ip, String tokenHash, TicketCategory category, String subject,
      String description) {
    Ticket ticket = new Ticket(null, category, subject, description);
    ticket.guestEmail = email.strip();
    ticket.guestIp = ip;
    ticket.guestTokenHash = tokenHash;
    return ticket;
  }

  public Long getId() {
    return id;
  }

  public User getAuthor() {
    return author;
  }

  public boolean isFromGuest() {
    return guestEmail != null;
  }

  public String getGuestEmail() {
    return guestEmail;
  }

  public String getGuestTokenHash() {
    return guestTokenHash;
  }

  public String getGuestIp() {
    return guestIp;
  }

  // only the hash is kept, so emailing the link again means a new one, and the old one stops working (#364)
  public void replaceGuestToken(String tokenHash) {
    this.guestTokenHash = tokenHash;
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

  // the queue answering means someone is on it, even if it was resolved before (#364)
  public void answered() {
    status = TicketStatus.IN_PROGRESS;
    updatedAt = LocalDateTime.now();
  }

  // twice would tell the author twice
  public void resolve() {
    if (status == TicketStatus.RESOLVED) {
      throw new StudyMatchException(ErrorCode.TICKET_ALREADY_RESOLVED);
    }
    status = TicketStatus.RESOLVED;
    updatedAt = LocalDateTime.now();
  }

}
