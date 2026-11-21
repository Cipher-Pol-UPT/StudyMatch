package com.studymatch.repositories;

import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.studymatch.models.Chat;
import com.studymatch.models.Message;

public interface MessageRepository extends JpaRepository<Message, Long> {

  // a chat's row in the chat list: its latest message and how many the user hasn't read
  interface ChatActivity {

    Message getLastMessage();

    long getUnread();

  }

  // latest by id that the user can see, two messages in the same instant still have different ids.
  // unread = from someone else and not covered by the user's lastReadAt, never opened means all of them.
  // messages hidden from the user, or by someone they blocked, never count (GR9)
  @Query("""
      select m as lastMessage,
        (select count(u) from Message u
          where u.chat = m.chat and u.author.number <> :number
            and (u.hiddenFrom is null or u.hiddenFrom.number <> :number)
            and not exists (select b from UserBlock b where b.blocker.number = :number and b.blocked = u.author)
            and not exists (select s from ChatSettings s
              where s.chat = u.chat and s.user.number = :number and s.lastReadAt >= u.sentAt)) as unread
      from Message m
      where m.chat in :chats
        and m.id = (select max(l.id) from Message l
          where l.chat = m.chat and (l.hiddenFrom is null or l.hiddenFrom.number <> :number))""")
  List<ChatActivity> findActivity(Collection<Chat> chats, Long number);

  // the side menu's total, same unread rule as findActivity
  @Query("""
      select count(u) from Message u
      where u.chat in :chats and u.author.number <> :number
        and (u.hiddenFrom is null or u.hiddenFrom.number <> :number)
        and not exists (select b from UserBlock b where b.blocker.number = :number and b.blocked = u.author)
        and not exists (select s from ChatSettings s
          where s.chat = u.chat and s.user.number = :number and s.lastReadAt >= u.sentAt)""")
  long countUnread(Collection<Chat> chats, Long number);

  // an open chat's newest messages the user can see, newest first (GR9)
  @EntityGraph(attributePaths = "author")
  @Query("""
      select m from Message m
      where m.chat = :chat and (m.hiddenFrom is null or m.hiddenFrom.number <> :number)
      order by m.id desc""")
  List<Message> findVisible(Chat chat, Long number, Limit limit);

  // scrolling up: the ones before the oldest on screen, by id so new messages don't shift the pages
  @EntityGraph(attributePaths = "author")
  @Query("""
      select m from Message m
      where m.chat = :chat and m.id < :before and (m.hiddenFrom is null or m.hiddenFrom.number <> :number)
      order by m.id desc""")
  List<Message> findVisibleBefore(Chat chat, Long before, Long number, Limit limit);

}
