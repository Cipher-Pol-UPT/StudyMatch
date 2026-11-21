package com.studymatch.repositories;

import org.springframework.data.jpa.repository.JpaRepository;

import com.studymatch.models.Chat;

public interface ChatRepository extends JpaRepository<Chat, Long> {

}
