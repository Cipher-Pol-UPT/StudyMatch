package com.studymatch.repositories;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.studymatch.enums.NotificationLevel;
import com.studymatch.models.Chat;
import com.studymatch.models.ChatSettings;
import com.studymatch.models.User;

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

  // the same for the level, a new row hasn't read anything yet
  @Modifying
  @Query(nativeQuery = true, value = """
      insert into chat_settings (chat_id, user_number, notification_level)
      values (:chatId, :userNumber, :notificationLevel)
      on conflict (chat_id, user_number)
      do update set notification_level = excluded.notification_level""")
  void upsertNotificationLevel(Long chatId, Long userNumber, String notificationLevel);

  interface MemberLevel {
    Long getNumber();

    NotificationLevel getLevel();
  }

  // everyone's levels when a message is sent, without loading the users. a member without a row is on the default
  @Query("select s.user.number as number, s.notificationLevel as level from ChatSettings s where s.chat = :chat")
  List<MemberLevel> findLevels(Chat chat);

  Optional<ChatSettings> findByChatAndUser(Chat chat, User user);

  // everyone's settings of a chat that's being deleted
  @Modifying
  @Query("delete from ChatSettings s where s.chat = :chat")
  void deleteByChat(Chat chat);

  // one person's, when they stop being a member
  @Modifying
  @Query("delete from ChatSettings s where s.chat = :chat and s.user = :user")
  void deleteByChatAndUser(Chat chat, User user);

  // a fresh ChatSettings brings the default level, so it isn't repeated in the SQL
  default void saveLastReadAt(ChatSettings settings) {
    upsertLastReadAt(settings.getChat().getId(), settings.getUser().getNumber(), settings.getLastReadAt(),
        settings.getNotificationLevel().name());
  }

}
