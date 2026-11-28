package com.studymatch.repositories;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.studymatch.models.Notification;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

  Page<Notification> findByRecipientNumber(Long recipientNumber, Pageable pageable);

  long countByRecipientNumberAndReadFalse(Long recipientNumber);

  Optional<Notification> findByIdAndRecipientNumber(Long id, Long recipientNumber);

  // a bulk update skips the loaded entities, so it clears them, or one left loaded would still say unread
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query("update Notification n set n.read = true where n.recipient.number = :recipientNumber and n.read = false")
  int markAllAsRead(Long recipientNumber);

}
