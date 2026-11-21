package com.studymatch.models;

import java.time.LocalDateTime;

import com.studymatch.enums.NotificationLevel;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotNull;

// one user's settings in one chat, created the first time they're needed (GR11)
@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = { "chat_id", "user_number" }))
public class ChatSettings {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne
  @JoinColumn(name = "chat_id")
  @NotNull
  private Chat chat;

  @ManyToOne
  @JoinColumn(name = "user_number")
  @NotNull
  private User user;

  // null until the chat is opened for the first time
  private LocalDateTime lastReadAt;

  @Enumerated(EnumType.STRING)
  @NotNull
  private NotificationLevel notificationLevel = NotificationLevel.ALL;

  protected ChatSettings() {
  }

  public ChatSettings(Chat chat, User user) {
    this.chat = chat;
    this.user = user;
  }

  public Long getId() {
    return id;
  }

  public Chat getChat() {
    return chat;
  }

  public User getUser() {
    return user;
  }

  public LocalDateTime getLastReadAt() {
    return lastReadAt;
  }

  public void markRead() {
    this.lastReadAt = LocalDateTime.now();
  }

  public NotificationLevel getNotificationLevel() {
    return notificationLevel;
  }

  public void setNotificationLevel(NotificationLevel notificationLevel) {
    this.notificationLevel = notificationLevel;
  }

}
