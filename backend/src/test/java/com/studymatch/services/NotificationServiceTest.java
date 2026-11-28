package com.studymatch.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

import com.studymatch.dtos.NotificationDTO;
import com.studymatch.dtos.PageDTO;
import com.studymatch.enums.NotificationTarget;
import com.studymatch.enums.NotificationType;
import com.studymatch.exceptions.ErrorCode;
import com.studymatch.exceptions.StudyMatchException;
import com.studymatch.models.ModelTestData;
import com.studymatch.models.Notification;
import com.studymatch.models.Student;
import com.studymatch.repositories.NotificationRepository;

class NotificationServiceTest {

  private static final Long NUMBER = 7L;
  private static final Long NOTIFICATION_ID = 30L;

  private NotificationRepository notificationRepository;
  private NotificationService notificationService;

  private Student ana;

  @BeforeEach
  void setUp() {
    notificationRepository = mock(NotificationRepository.class);
    notificationService = new NotificationService(notificationRepository);

    ana = ModelTestData.student("Ana Test");
    ReflectionTestUtils.setField(ana, "number", NUMBER);
  }

  private Notification notification() {
    Notification notification = new Notification(ana, NotificationType.STUDYROOM_CANCELLED,
        NotificationTarget.STUDYROOM, 20L, Map.of("subject", "Test Subject"));
    ReflectionTestUtils.setField(notification, "id", NOTIFICATION_ID);
    return notification;
  }

  // notify

  @Test
  @DisplayName("A notification is saved unread for its recipient, with its code, params and target")
  void notifySavesUnreadNotification() {
    // Act
    notificationService.notify(ana, NotificationType.STUDYROOM_LEFT, NotificationTarget.STUDYROOM, 20L,
        Map.of("student", "Bruno Test"));

    // Assert
    ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
    verify(notificationRepository).save(saved.capture());
    Notification notification = saved.getValue();
    assertEquals(ana, notification.getRecipient());
    assertEquals(NotificationType.STUDYROOM_LEFT, notification.getType());
    assertEquals(Map.of("student", "Bruno Test"), notification.getParams());
    assertEquals(NotificationTarget.STUDYROOM, notification.getTarget());
    assertEquals(20L, notification.getTargetId());
    assertFalse(notification.isRead());
    assertNotNull(notification.getCreatedAt());
  }

  @Test
  @DisplayName("A notification that opens nothing has no target")
  void notifyWithoutTarget() {
    // Act
    notificationService.notify(ana, NotificationType.ACCOUNT_UNBLOCKED, null, null, Map.of());

    // Assert
    ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
    verify(notificationRepository).save(saved.capture());
    assertNull(saved.getValue().getTarget());
    assertNull(saved.getValue().getTargetId());
    assertTrue(saved.getValue().getParams().isEmpty());
  }

  @Test
  @DisplayName("Notifying several people saves one notification each, all at once")
  @SuppressWarnings("unchecked")
  void notifyAllSavesOneEach() {
    // Arrange
    Student bruno = ModelTestData.student("Bruno Test");

    // Act
    notificationService.notifyAll(List.of(ana, bruno), NotificationType.STUDYROOM_UPDATED,
        NotificationTarget.STUDYROOM, 20L, Map.of("subject", "Test Subject"));

    // Assert
    ArgumentCaptor<List<Notification>> saved = ArgumentCaptor.forClass(List.class);
    verify(notificationRepository).saveAll(saved.capture());
    assertEquals(List.of(ana, bruno), saved.getValue().stream().map(Notification::getRecipient).toList());
    assertTrue(saved.getValue().stream().allMatch(n -> n.getType() == NotificationType.STUDYROOM_UPDATED));
  }

  // view notifications (#144)

  @Test
  @DisplayName("The list is the user's own, newest first, 20 per page")
  void listsOwnNotificationsNewestFirst() {
    // Arrange
    Notification notification = notification();
    when(notificationRepository.findByRecipientNumber(eq(NUMBER), any()))
        .thenReturn(new PageImpl<>(List.of(notification), PageRequest.of(1, 20), 21));

    // Act
    PageDTO<NotificationDTO> page = notificationService.listNotifications(ana, 1);

    // Assert
    ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
    verify(notificationRepository).findByRecipientNumber(eq(NUMBER), pageable.capture());
    assertEquals(1, pageable.getValue().getPageNumber());
    assertEquals(20, pageable.getValue().getPageSize());
    assertEquals(Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")), pageable.getValue().getSort());
    assertEquals(21, page.totalElements());
    NotificationDTO item = page.content().getFirst();
    assertEquals(NOTIFICATION_ID, item.id());
    assertEquals(NotificationType.STUDYROOM_CANCELLED, item.type());
    assertEquals(Map.of("subject", "Test Subject"), item.params());
    assertEquals(NotificationTarget.STUDYROOM, item.target());
    assertEquals(20L, item.targetId());
    assertFalse(item.read());
  }

  @Test
  @DisplayName("A negative page is the first page")
  void negativePageIsFirst() {
    // Arrange
    when(notificationRepository.findByRecipientNumber(eq(NUMBER), any())).thenReturn(new PageImpl<>(List.of()));

    // Act
    notificationService.listNotifications(ana, -1);

    // Assert
    ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
    verify(notificationRepository).findByRecipientNumber(eq(NUMBER), pageable.capture());
    assertEquals(0, pageable.getValue().getPageNumber());
  }

  @Test
  @DisplayName("The unread count is the user's own unread notifications")
  void countsUnread() {
    // Arrange
    when(notificationRepository.countByRecipientNumberAndReadFalse(NUMBER)).thenReturn(3L);

    // Act + Assert
    assertEquals(3L, notificationService.countUnread(ana));
  }

  @Test
  @DisplayName("Marking one of your notifications as read saves it read")
  void marksOwnNotificationAsRead() {
    // Arrange
    Notification notification = notification();
    when(notificationRepository.findByIdAndRecipientNumber(NOTIFICATION_ID, NUMBER))
        .thenReturn(Optional.of(notification));

    // Act
    notificationService.markAsRead(NOTIFICATION_ID, ana);

    // Assert
    assertTrue(notification.isRead());
    verify(notificationRepository).save(notification);
  }

  @Test
  @DisplayName("Someone else's notification, or one that doesn't exist, is NOT_FOUND and nothing is saved")
  void othersNotificationIsNotFound() {
    // Act - findByIdAndRecipientNumber is empty by default, as it is for another user's notification
    StudyMatchException e = assertThrows(StudyMatchException.class,
        () -> notificationService.markAsRead(NOTIFICATION_ID, ana));

    // Assert
    assertEquals(ErrorCode.NOT_FOUND, e.getCode());
    verify(notificationRepository, never()).save(any());
  }

  @Test
  @DisplayName("Marking all as read only touches the user's own")
  void marksAllOwnAsRead() {
    // Act
    notificationService.markAllAsRead(ana);

    // Assert
    verify(notificationRepository).markAllAsRead(NUMBER);
  }

}
