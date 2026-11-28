package com.studymatch.services;

import java.util.Collection;
import java.util.Map;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.studymatch.dtos.NotificationDTO;
import com.studymatch.dtos.PageDTO;
import com.studymatch.enums.NotificationTarget;
import com.studymatch.enums.NotificationType;
import com.studymatch.exceptions.ErrorCode;
import com.studymatch.exceptions.StudyMatchException;
import com.studymatch.models.Notification;
import com.studymatch.models.User;
import com.studymatch.repositories.NotificationRepository;

// the other services call notify when their use case says the System notifies someone, so this one depends on none
@Service
public class NotificationService {

  private static final int NOTIFICATIONS_PER_PAGE = 20;

  private final NotificationRepository notificationRepository;

  public NotificationService(NotificationRepository notificationRepository) {
    this.notificationRepository = notificationRepository;
  }

  // in the caller's transaction, so a use case that fails leaves no notification behind
  @Transactional
  public void notify(User recipient, NotificationType type, NotificationTarget target, Long targetId,
      Map<String, Object> params) {
    notificationRepository.save(new Notification(recipient, type, target, targetId, params));
  }

  @Transactional
  public void notifyAll(Collection<? extends User> recipients, NotificationType type, NotificationTarget target,
      Long targetId, Map<String, Object> params) {
    notificationRepository.saveAll(recipients.stream()
        .map(recipient -> new Notification(recipient, type, target, targetId, params))
        .toList());
  }

  // newest first, the id breaks a tie between two made in the same instant
  @Transactional(readOnly = true)
  public PageDTO<NotificationDTO> listNotifications(User user, int page) {
    PageRequest pageRequest = PageRequest.of(Math.max(page, 0), NOTIFICATIONS_PER_PAGE,
        Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
    return PageDTO.from(notificationRepository.findByRecipientNumber(user.getNumber(), pageRequest),
        NotificationDTO::from);
  }

  public long countUnread(User user) {
    return notificationRepository.countByRecipientNumberAndReadFalse(user.getNumber());
  }

  // someone else's notification is NOT_FOUND, the same as one that doesn't exist
  @Transactional
  public void markAsRead(Long notificationId, User user) {
    Notification notification = notificationRepository.findByIdAndRecipientNumber(notificationId, user.getNumber())
        .orElseThrow(() -> new StudyMatchException(ErrorCode.NOT_FOUND));
    notification.markAsRead();
    notificationRepository.save(notification);
  }

  @Transactional
  public void markAllAsRead(User user) {
    notificationRepository.markAllAsRead(user.getNumber());
  }

}
