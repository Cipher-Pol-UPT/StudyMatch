package com.studymatch.dtos;

import java.time.LocalDateTime;
import java.util.Map;

import com.studymatch.enums.NotificationTarget;
import com.studymatch.enums.NotificationType;
import com.studymatch.models.Notification;

// the frontend translates type with params, and opens target with targetId when there is one
public record NotificationDTO(Long id, NotificationType type, Map<String, Object> params, NotificationTarget target,
    Long targetId, LocalDateTime createdAt, boolean read) {

  public static NotificationDTO from(Notification notification) {
    return new NotificationDTO(notification.getId(), notification.getType(), notification.getParams(),
        notification.getTarget(), notification.getTargetId(), notification.getCreatedAt(), notification.isRead());
  }

}
