package com.studymatch.repositories;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.studymatch.models.Message;
import com.studymatch.models.Reaction;

import jakarta.persistence.LockModeType;

public interface ReactionRepository extends JpaRepository<Reaction, Long> {

  // the reactions to a page of messages that the user can see (GR9)
  @Query("""
      select r from Reaction r
      join fetch r.user
      where r.message in :messages and (r.hiddenFrom is null or r.hiddenFrom.number <> :number)
      order by r.id""")
  List<Reaction> findVisible(Collection<Message> messages, Long number);

  // locked, so two tabs changing or removing it go one after the other
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select r from Reaction r where r.message = :message and r.user.number = :number")
  Optional<Reaction> findLocked(Message message, Long number);

  // the first one has no row to lock, two tabs at once would both insert and trip the unique pair.
  // 0 when the other tab just put the same emoji
  @Modifying
  @Query(nativeQuery = true, value = """
      insert into reaction (message_id, user_number, emoji, hidden_from_number)
      values (:messageId, :number, :emoji, :hiddenFromNumber)
      on conflict (message_id, user_number)
      do update set emoji = excluded.emoji, hidden_from_number = excluded.hidden_from_number
      where reaction.emoji <> excluded.emoji""")
  int upsert(Long messageId, Long number, String emoji, Long hiddenFromNumber);

}
