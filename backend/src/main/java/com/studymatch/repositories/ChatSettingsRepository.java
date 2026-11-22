package com.studymatch.repositories;

import java.time.LocalDateTime;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.studymatch.models.Chat;
import com.studymatch.models.ChatSettings;

public interface ChatSettingsRepository extends JpaRepository<ChatSettings, Long> {

  // two tabs opening a chat for the first time at once would both insert and trip the unique pair,
  // so the row is created or updated in one go. the level only counts when the row is new.
  // greatest so two reads finishing out of order never move it back
  @Modifying
  @Query(nativeQuery = true, value = """
      insert into chat_settings (chat_id, user_number, last_read_at, notification_level)
      values (:chatId, :userNumber, :lastReadAt, :notificationLevel)
      on conflict (chat_id, user_number)
      do update set last_read_at = greatest(chat_settings.last_read_at, excluded.last_read_at)""")
  void upsertLastReadAt(Long chatId, Long userNumber, LocalDateTime lastReadAt, String notificationLevel);

  // everyone's settings of a chat that's being deleted
  @Modifying
  @Query("delete from ChatSettings s where s.chat = :chat")
  void deleteByChat(Chat chat);

  // a fresh ChatSettings brings the default level, so it isn't repeated in the SQL
  default void saveLastReadAt(ChatSettings settings) {
    upsertLastReadAt(settings.getChat().getId(), settings.getUser().getNumber(), settings.getLastReadAt(),
        settings.getNotificationLevel().name());
  }

}
