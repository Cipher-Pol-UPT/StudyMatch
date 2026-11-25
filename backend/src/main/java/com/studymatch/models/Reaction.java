package com.studymatch.models;

import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

// one user's emoji on a message, one each, a new one replaces it (#216)
@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = { "message_id", "user_number" }))
public class Reaction {

  // an emoji can be many characters, a family or a couple with skin tones are the longest
  public static final int MAX_EMOJI_LENGTH = 64;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  // the database deletes it with the message (GR10)
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "message_id")
  @OnDelete(action = OnDeleteAction.CASCADE)
  @NotNull
  private Message message;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_number")
  @NotNull
  private User user;

  @NotBlank
  @Column(length = MAX_EMOJI_LENGTH)
  private String emoji;

  // the blocker, same as a message's (GR9)
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "hidden_from_number")
  private User hiddenFrom;

  protected Reaction() {
  }

  public Reaction(Message message, User user, String emoji, User hiddenFrom) {
    this.message = message;
    this.user = user;
    this.emoji = emoji;
    this.hiddenFrom = hiddenFrom;
  }

  public Long getId() {
    return id;
  }

  public Message getMessage() {
    return message;
  }

  public User getUser() {
    return user;
  }

  public String getEmoji() {
    return emoji;
  }

  public User getHiddenFrom() {
    return hiddenFrom;
  }

  public void replace(String emoji, User hiddenFrom) {
    this.emoji = emoji;
    this.hiddenFrom = hiddenFrom;
  }

  // by number, the user may come from another request than the reaction
  public boolean isHiddenFrom(User user) {
    return hiddenFrom != null && hiddenFrom.getNumber().equals(user.getNumber());
  }

}
