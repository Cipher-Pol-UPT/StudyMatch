package com.studymatch.models;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.studymatch.enums.NotificationTarget;
import com.studymatch.enums.NotificationType;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;

// a code and its params, not text, so the frontend shows it in the user's language. never deleted
@Entity
@Table(indexes = @Index(columnList = "recipient_number, created_at"))
public class Notification {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @NotNull
  private User recipient;

  @Enumerated(EnumType.STRING)
  @NotNull
  private NotificationType type;

  @JdbcTypeCode(SqlTypes.JSON)
  private Map<String, Object> params;

  // null when there's no page to open. an achievement's id is null too, the tutor has one achievements page
  @Enumerated(EnumType.STRING)
  private NotificationTarget target;

  private Long targetId;

  @NotNull
  private LocalDateTime createdAt;

  private boolean read;

  protected Notification() {
  }

  public Notification(User recipient, NotificationType type, NotificationTarget target, Long targetId,
      Map<String, Object> params) {
    this.recipient = recipient;
    this.type = type;
    this.target = target;
    this.targetId = targetId;
    this.params = new HashMap<>(params);
    this.createdAt = LocalDateTime.now();
  }

  // the first message since the chat was last read, the next ones are counted into it (GR11)
  public static Notification forChatMessage(User recipient, Long chatId, String chatName, boolean mentioned) {
    return new Notification(recipient, NotificationType.CHAT_MESSAGES, NotificationTarget.CHAT, chatId,
        Map.of("chat", chatName, "count", 1, "mentions", mentioned ? 1 : 0));
  }

  // one more, under the chat's current name, and back to the top of the list. a new map so the change is seen
  public void countChatMessage(String chatName, boolean mentioned) {
    Map<String, Object> counted = new HashMap<>(params);
    counted.put("chat", chatName);
    counted.put("count", ((Number) params.get("count")).intValue() + 1);
    counted.put("mentions", ((Number) params.get("mentions")).intValue() + (mentioned ? 1 : 0));
    this.params = counted;
    this.createdAt = LocalDateTime.now();
  }

  public Long getId() {
    return id;
  }

  public User getRecipient() {
    return recipient;
  }

  public NotificationType getType() {
    return type;
  }

  public Map<String, Object> getParams() {
    return params;
  }

  public NotificationTarget getTarget() {
    return target;
  }

  public Long getTargetId() {
    return targetId;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public boolean isRead() {
    return read;
  }

  public void markAsRead() {
    this.read = true;
  }

}
