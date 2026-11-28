package com.studymatch.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.studymatch.models.TicketReply;

public interface TicketReplyRepository extends JpaRepository<TicketReply, Long> {

  // the thread, oldest first
  List<TicketReply> findByTicket_IdOrderByIdAsc(Long ticketId);

}
