package com.studymatch.repositories;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import com.studymatch.models.Chat;
import com.studymatch.models.CustomChat;
import com.studymatch.models.DirectChat;
import com.studymatch.models.Group;
import com.studymatch.models.GroupChat;

import jakarta.persistence.LockModeType;

public interface ChatRepository extends JpaRepository<Chat, Long> {

  // smaller number first, the same order DirectChat saves them in
  @Query("select d from DirectChat d where d.firstUser.number = :first and d.secondUser.number = :second")
  Optional<DirectChat> findDirectChat(Long first, Long second);

  // the user's DMs, on either side
  @Query("select d from DirectChat d where d.firstUser.number = :number or d.secondUser.number = :number")
  List<DirectChat> findDirectChatsOf(Long number);

  // changing the members (#228): two at once would both write the next position, so the second waits for the
  // first. CustomChat and not Chat, postgres can't lock the outer joins a Chat query has for its subclasses
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select c from CustomChat c where c.id = :id")
  Optional<CustomChat> findLockedCustomChat(Long id);

  // the group chats the user is a member of, the owner included
  @Query("select c from CustomChat c join c.members m where m.number = :number")
  List<CustomChat> findCustomChatsOf(Long number);

  // the assignment chats of the groups the student is in (#236)
  @Query("select c from GroupChat c join c.group g join g.members m where m.number = :number")
  List<GroupChat> findGroupChatsOf(Long number);

  // the chats of groups being deleted or edited
  @Query("select c from GroupChat c where c.group in :groups")
  List<GroupChat> findGroupChatsFor(Collection<Group> groups);

}
