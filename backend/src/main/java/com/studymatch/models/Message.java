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

}
