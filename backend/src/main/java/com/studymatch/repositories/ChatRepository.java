package com.studymatch.repositories;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.studymatch.models.Chat;
import com.studymatch.models.DirectChat;

public interface ChatRepository extends JpaRepository<Chat, Long> {

  // smaller number first, the same order DirectChat saves them in
  @Query("select d from DirectChat d where d.firstUser.number = :first and d.secondUser.number = :second")
  Optional<DirectChat> findDirectChat(Long first, Long second);

}
