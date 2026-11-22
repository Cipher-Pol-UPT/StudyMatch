package com.studymatch.models;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Entity
public class Message {

  public static final int MAX_TEXT_LENGTH = 2000;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne
  @NotNull
  private Chat chat;

  @ManyToOne
  @NotNull
  private User author;

  @NotBlank
  @Size(max = MAX_TEXT_LENGTH)
  @Column(length = MAX_TEXT_LENGTH)
  private String text;

  @NotNull
  private LocalDateTime sentAt;

  // null until edited, then the message shows "(edited)" (GR10)
  private LocalDateTime editedAt;

  // the blocker, when a blocked user sent it in their DM. stays hidden from them even after unblocking (GR9)
  @ManyToOne
  private User hiddenFrom;

  protected Message() {
  }

  public Message(Chat chat, User author, String text) {
    this.chat = chat;
    this.author = author;
    this.text = text;
    this.sentAt = LocalDateTime.now();
  }

  public Long getId() {
    return id;
  }

  public Chat getChat() {
    return chat;
  }

  public User getAuthor() {
    return author;
  }

  public String getText() {
    return text;
  }

  public LocalDateTime getSentAt() {
    return sentAt;
  }

  public LocalDateTime getEditedAt() {
    return editedAt;
  }

  public boolean isEdited() {
    return editedAt != null;
  }

  public void edit(String text) {
    this.text = text;
    this.editedAt = LocalDateTime.now();
  }

  public User getHiddenFrom() {
    return hiddenFrom;
  }

  public void hideFrom(User blocker) {
    this.hiddenFrom = blocker;
  }

  // by number, the user may come from another request than the message
  public boolean isHiddenFrom(User user) {
    return hiddenFrom != null && hiddenFrom.getNumber().equals(user.getNumber());
  }

}
