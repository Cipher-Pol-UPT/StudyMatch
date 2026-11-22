package com.studymatch.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.studymatch.models.Chat;
import com.studymatch.models.CustomChat;
import com.studymatch.models.DirectChat;

public interface ChatRepository extends JpaRepository<Chat, Long> {

  // smaller number first, the same order DirectChat saves them in
  @Query("select d from DirectChat d where d.firstUser.number = :first and d.secondUser.number = :second")
  Optional<DirectChat> findDirectChat(Long first, Long second);

  // the user's DMs, on either side
  @Query("select d from DirectChat d where d.firstUser.number = :number or d.secondUser.number = :number")
  List<DirectChat> findDirectChatsOf(Long number);

  // the group chats the user is a member of, the owner included
  @Query("select c from CustomChat c join c.members m where m.number = :number")
  List<CustomChat> findCustomChatsOf(Long number);

}
